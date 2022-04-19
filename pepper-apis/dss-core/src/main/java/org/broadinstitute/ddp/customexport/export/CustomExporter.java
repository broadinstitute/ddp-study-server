package org.broadinstitute.ddp.customexport.export;

import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.opencsv.CSVWriter;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.customexport.collectors.ComplexChildResponseCollector;
import org.broadinstitute.ddp.customexport.collectors.ParentActivityResponseCollector;
import org.broadinstitute.ddp.customexport.constants.CustomExportConfigFile;
import org.broadinstitute.ddp.customexport.model.CustomExportParticipant;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.export.ActivityResponseMapping;
import org.broadinstitute.ddp.export.ComponentDataSupplier;
import org.broadinstitute.ddp.export.ExportUtil;
import org.broadinstitute.ddp.export.collectors.ActivityAttributesCollector;
import org.broadinstitute.ddp.export.collectors.ActivityResponseCollector;
import org.broadinstitute.ddp.export.collectors.ParticipantMetadataFormatter;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.study.Participant;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.jdbi.v3.core.Handle;

@Slf4j
public class CustomExporter {
    // A cache for user auth0 emails, storing (auth0UserId -> email).
    private static final Map<String, String> emailStore = new HashMap<>();
    private static final String familyIdHeader = "familyId";

    private final Config exportConfig;
    private final String customGuid;
    private final String customActivity;
    private final RestHighLevelClient elasticsearchClient;

    CustomExporter(Config mainConfig, Config exportConfig) {
        this.exportConfig = exportConfig;
        this.customActivity = exportConfig.getString(CustomExportConfigFile.ACTIVITY);
        this.customGuid = exportConfig.getString(CustomExportConfigFile.STUDY_GUID);
        try {
            elasticsearchClient = ElasticsearchServiceUtil.getElasticsearchClient(mainConfig);
        } catch (MalformedURLException e) {
            throw new DDPException("Cannot initialize CustomExporter due to error getting ElasticSearch client", e);
        }
    }

    List<CustomExportParticipant> extractParticipantDataSetByIds(Handle handle, StudyDto studyDto, Set<Long> userIds) {
        List<Participant> baseParticipants = ExportUtil.extractParticipantDataSetByIds(
                handle, studyDto, userIds, emailStore);
        return createCustomParticipantsFrom(baseParticipants, handle, studyDto, elasticsearchClient);
    }

    private static List<CustomExportParticipant> createCustomParticipantsFrom(List<Participant> participants, Handle handle,
                                                                              StudyDto studyDto,
                                                                              RestHighLevelClient esClient) {

        // Here, we take the participants we constructed from the database and add the family ID from ElasticSearch
        List<CustomExportParticipant> customExportParticipants = new ArrayList<>();
        for (Participant p : participants) {
            String familyId = getFamilyId(handle, studyDto, p.getUser().getGuid(), esClient);
            if (familyId != null) {
                customExportParticipants.add(new CustomExportParticipant(familyId, p));
            } else {
                log.error("Skipped participant with GUID = '{}' from the export batch because dsm.familyId is null",
                        p.getUser().getGuid());
            }
        }
        return customExportParticipants;
    }

    private static String getFamilyId(Handle handle, StudyDto studyDto, String userGuid, RestHighLevelClient esClient) throws DDPException {
        String esIndex = ElasticsearchServiceUtil.getIndexForStudy(handle, studyDto, ElasticSearchIndexType.PARTICIPANTS_STRUCTURED);
        GetRequest getRequest = new GetRequest(esIndex, "_doc", userGuid);
        String[] includes = {"dsm.familyId"};
        getRequest.fetchSourceContext(new FetchSourceContext(true, includes, null));
        GetResponse esResponse;
        try {
            esResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new DDPException("Failed to get family ID for participant with guid " + userGuid + " due to failure of "
                    + "ElasticSearch get request", e);
        }
        Map<String, Object> source = esResponse.getSource();

        if (source == null) {
            throw new DDPException("Failed to get family ID for participant with guid " + userGuid + " because source returned from "
                    + "ElasticSearch is null");
        }

        Object familyId = source.get("dsm") == null ? null : ((Map) source.get("dsm")).get("familyId");

        return familyId == null ? null : familyId.toString();
    }

    public static void clearCachedAuth0Emails() {
        ExportUtil.clearCachedAuth0Emails(emailStore);
    }

    /**
     * Find and set the max number of instances seen per participant across the study for each given activity extract.
     * Activities that are defined with only one instance per participant will not be computed so the counts might not
     * be totally accurate. Otherwise, this will find the current number of instances for each activity.
     *
     * @param handle     the database handle
     * @param activities the list of activities to look at
     */
    static void computeMaxInstancesSeen(Handle handle, List<CustomActivityExtract> activities) {
        var instanceDao = handle.attach(ActivityInstanceDao.class);
        for (CustomActivityExtract activity : activities) {
            ExportUtil.computeMaxInstancesSeen(instanceDao, activity);
            if (activity.getChildExtracts() != null) {
                for (List<CustomActivityExtract> childActivities : activity.getChildExtracts().values()) {
                    for (CustomActivityExtract childActivity : childActivities) {
                        ExportUtil.computeMaxInstancesSeen(instanceDao, childActivity);
                    }
                }
            }
        }
    }

    /**
     * Find and set the attribute names seen across all participants in study for each given activity extract.
     *
     * @param handle     the database handle
     * @param activities the list of activities to look at
     */
    static void computeActivityAttributesSeen(Handle handle, List<CustomActivityExtract> activities) {
        var instanceDao = handle.attach(ActivityInstanceDao.class);
        for (CustomActivityExtract activity : activities) {
            ExportUtil.computeActivityAttributesSeen(instanceDao, activity);
            if (activity.getChildExtracts() != null) {
                for (List<CustomActivityExtract> childExtracts : activity.getChildExtracts().values()) {
                    for (CustomActivityExtract childExtract : childExtracts) {
                        ExportUtil.computeActivityAttributesSeen(instanceDao, childExtract);
                    }
                }
            }
        }
    }

    /**
     * Extract all versions of an activity for a study
     *
     * @param handle      the database handle
     * @param activityDto the activity
     * @param studyGuid   the study guid
     * @return list of extracts, in ascending order by version
     */
    private List<CustomActivityExtract> extractVersionsOfActivity(Handle handle, ActivityDto activityDto, String studyGuid,
                                                                  List<String> excludedVersions) {
        JdbiActivityVersion jdbiActivityVersion = handle.attach(JdbiActivityVersion.class);
        FormActivityDao formActivityDao = handle.attach(FormActivityDao.class);
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        ActivityDefStore store = ActivityDefStore.getInstance();
        List<CustomActivityExtract> activities = new ArrayList<>();

        List<ActivityVersionDto> versionDtos = jdbiActivityVersion.findAllVersionsInAscendingOrder(activityDto.getActivityId());
        if (excludedVersions != null) {
            versionDtos = versionDtos.stream().filter(v -> !excludedVersions.contains(v.getVersionTag())).collect(Collectors.toList());
        }

        for (ActivityVersionDto versionDto : versionDtos) {
            // Only supports form activities for now.

            List<ActivityDto> childActivityDtos = jdbiActivity.findChildActivitiesByParentId(activityDto.getActivityId());
            Map<String, List<CustomActivityExtract>> childExtracts = new HashMap<>();

            if (childActivityDtos != null && !childActivityDtos.isEmpty()) {
                // Create child activity extracts
                for (ActivityDto childActivityDto : childActivityDtos) {
                    long childActivityId = childActivityDto.getActivityId();
                    List<ActivityVersionDto> childVersions = jdbiActivityVersion.findAllVersionsInAscendingOrder(childActivityId);
                    if (childVersions != null) {
                        List<CustomActivityExtract> subExtracts = new ArrayList<>();
                        for (ActivityVersionDto childVersion : childVersions) {
                            FormActivityDef childFormActivityDef = addDefByVersion(studyGuid, childVersion, childActivityDto,
                                    formActivityDao, store);
                            subExtracts.add(new CustomActivityExtract(childFormActivityDef,
                                    childVersion, canHaveMultiple(childFormActivityDef)));
                        }
                        childExtracts.put(childActivityDto.getActivityCode(), subExtracts);

                    }
                }
            }
            FormActivityDef def = addDefByVersion(studyGuid, versionDto, activityDto, formActivityDao, store);
            activities.add(new CustomActivityExtract(def, versionDto, childExtracts, canHaveMultiple(def)));
        }

        return activities;
    }

    private boolean canHaveMultiple(FormActivityDef activityDef) {
        return (activityDef.getMaxInstancesPerUser() == null || activityDef.getMaxInstancesPerUser() > 1)
                && activityDef.canDeleteInstances();
    }

    private FormActivityDef addDefByVersion(String studyGuid, ActivityVersionDto versionDto, ActivityDto activityDto,
                                            FormActivityDao formActivityDao, ActivityDefStore store) {
        String versionTag = versionDto.getVersionTag();
        String activityCode = activityDto.getActivityCode();
        FormActivityDef def = store.getActivityDef(studyGuid, activityCode, versionTag);
        if (def == null) {
            def = formActivityDao.findDefByDtoAndVersion(activityDto, versionDto);
            store.setActivityDef(studyGuid, activityCode, versionTag, def);
        }
        return def;
    }

    /**
     * Extracts all versions of custom export's activity
     *
     * @param handle the database handle
     * @return list of extracts
     */
    List<CustomActivityExtract> extractActivity(Handle handle) {
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);

        Optional<ActivityDto> activityDtoOptional = jdbiActivity.findActivityByStudyGuidAndCode(customGuid, customActivity);
        if (activityDtoOptional.isEmpty()) {
            log.error("Activity {} DTO not found for custom export", customActivity);
            return null;
        }
        ActivityDto activityDto = activityDtoOptional.get();
        List<CustomActivityExtract> activities = extractVersionsOfActivity(handle, activityDto, customGuid,
                exportConfig.getStringList(CustomExportConfigFile.EXCLUDED_ACTIVITY_VERSIONS));

        log.info("Custom export found {} versions of activity {}", activities.size(), customActivity);

        return activities;
    }

    /**
     * Export the given dataset to the given output using CSV formatting. Caller is responsible for closing the given output writer.
     *
     * @param activities   the list of activity data for a study, with additional data pre-computed
     * @param participants the participant data for a study
     * @param output       the output writer to use
     * @return number of participant records written
     * @throws IOException if error while writing
     */
    int exportDataSetAsCsv(StudyDto studyDto, List<CustomActivityExtract> activities,
                           Iterator<CustomExportParticipant> participants, Writer output) throws IOException {
        List<String> firstFields = exportConfig.getStringList(CustomExportConfigFile.FIRST_FIELDS);
        List<String> excludedParticipantFields = new ArrayList<>(exportConfig
                .getStringList(CustomExportConfigFile.EXCLUDED_PARTICIPANT_FIELDS));
        List<String> excludedActivityFields = new ArrayList<>(exportConfig.getStringList(CustomExportConfigFile.EXCLUDED_ACTIVITY_FIELDS));
        ParticipantMetadataFormatter participantMetaFmt = new ParticipantMetadataFormatter(excludedParticipantFields);
        List<String> headers = new LinkedList<>(participantMetaFmt.headers());
        headers.add(familyIdHeader);

        Map<String, Integer> activityTagToNormalizedMaxInstanceCounts = new HashMap<>();
        Map<String, ParentActivityResponseCollector> responseCollectors = new HashMap<>();
        Map<String, ActivityAttributesCollector> attributesCollectors = new HashMap<>();

        for (CustomActivityExtract activity : activities) {
            Integer maxInstances = activity.getMaxInstancesSeen();
            if (maxInstances == null || maxInstances < 1) {
                log.warn("Found max instances count {} for activity tag {}, defaulting to 1", maxInstances, activity.getTag());
                // NOTE: default to one so we always have one set of columns even if no participant has an instance.
                maxInstances = 1;
            }

            // Create attribute and response collectors to use for generating headers
            ActivityAttributesCollector attributesCollector = new ActivityAttributesCollector(activity
                    .getAttributesSeen(firstFields, excludedParticipantFields));
            attributesCollectors.put(activity.getTag(), attributesCollector);
            ActivityResponseCollector mainResponseCollector = new ActivityResponseCollector(activity.getDefinition(), firstFields,
                    excludedActivityFields);

            Map<String, List<ActivityResponseCollector>> childResponseCollectors = new HashMap<>();
            Map<String, List<ComplexChildResponseCollector>> multiChildResponseCollectors =
                    new HashMap<>(); // For child activities with multiple potential instances, we will put everything in one field
            Map<String, List<CustomActivityExtract>> childExtracts = activity.getChildExtracts();

            // Construct response collectors for child activities
            for (String childExtractCode : childExtracts.keySet()) {
                List<ComplexChildResponseCollector> multiList = new ArrayList<>();
                List<ActivityResponseCollector> childVersionResponseCollectors = new ArrayList<>();

                for (CustomActivityExtract childExtract : childExtracts.get(childExtractCode)) {
                    if (childExtract.getCanHaveMultiple()) {
                        ActivityDef childActivityDefinition = childExtract.getDefinition();
                        multiList.add(new ComplexChildResponseCollector(childActivityDefinition));
                    } else {
                        ActivityDef childActivityDefinition = childExtract.getDefinition();
                        childVersionResponseCollectors.add(new ActivityResponseCollector(childActivityDefinition));
                    }
                }
                if (!childVersionResponseCollectors.isEmpty()) {
                    childResponseCollectors.put(childExtractCode, childVersionResponseCollectors);
                }
                if (!multiList.isEmpty()) {
                    multiChildResponseCollectors.put(childExtractCode, multiList);
                }
            }
            ParentActivityResponseCollector responseCollector = new ParentActivityResponseCollector(mainResponseCollector,
                    childResponseCollectors, multiChildResponseCollectors);

            activityTagToNormalizedMaxInstanceCounts.put(activity.getTag(), maxInstances);
            responseCollectors.put(activity.getTag(), responseCollector);

            for (var i = 1; i <= maxInstances; i++) {
                // Add the headers
                headers.addAll(attributesCollector.headers());
                headers.addAll(mainResponseCollector.getHeaders());
                List<String> singleChildCodes = new ArrayList<>(childResponseCollectors.keySet());
                singleChildCodes.sort(String::compareTo);
                // Add headers for child activities that only have a single instance
                for (String singleChildCode : singleChildCodes) {
                    List<ActivityResponseCollector> sortedChildResponseCollectors = childResponseCollectors.get(singleChildCode);
                    for (ActivityResponseCollector sortedChildResponseCollector : sortedChildResponseCollectors) {
                        headers.addAll(sortedChildResponseCollector.getHeaders());
                    }
                }

                // Child activities that can have multiple instances will have all data for a given activity in a single column
                headers.addAll(multiChildResponseCollectors.keySet());
            }
        }

        CSVWriter writer = new CSVWriter(output);
        writer.writeNext(headers.toArray(new String[]{}), false);

        int numWritten = 0;

        // Write the data to file
        while (participants.hasNext()) {
            CustomExportParticipant customPt = participants.next();
            Participant pt = customPt.getParticipant();
            List<String> row;
            try {
                row = new LinkedList<>(participantMetaFmt.format(pt.getStatus(), pt.getUser()));
                row.add(customPt.getFamilyId());
                ComponentDataSupplier supplier = new ComponentDataSupplier(
                        pt.getUser().getAddress(),
                        pt.getNonDefaultMailAddresses(),
                        pt.getProviders());
                for (CustomActivityExtract activity : activities) {
                    String activityTag = activity.getTag();
                    int maxInstances = activityTagToNormalizedMaxInstanceCounts.get(activityTag);
                    ParentActivityResponseCollector responseCollector = responseCollectors.get(activityTag);
                    ActivityAttributesCollector attributesCollector = attributesCollectors.get(activityTag);

                    List<ActivityResponse> mainInstances = pt.getResponses(activityTag).stream()
                            .sorted(Comparator.comparing(ActivityResponse::getCreatedAt))
                            .collect(Collectors.toList());

                    Map<ActivityResponse, List<ActivityResponseMapping>> instances = new HashMap<>();
                    for (ActivityResponse r : mainInstances) {
                        List<ActivityResponseMapping> simpleChildInstances = new ArrayList<>();
                        List<ActivityResponseMapping> complexChildInstances = new ArrayList<>();
                        Map<String, List<CustomActivityExtract>> childExtracts = activity.getChildExtracts();

                        for (String childActivityCode : childExtracts.keySet()) {
                            // Make sure all simple children are added before all complex children
                            List<ActivityResponse> simpleChildSubInstances = new ArrayList<>();
                            List<ActivityResponse> complexChildSubInstances = new ArrayList<>();
                            for (CustomActivityExtract childExtract : childExtracts.get(childActivityCode)) {
                                List<ActivityResponse> responses = pt.getResponses(childExtract.getTag())
                                        .stream().sorted(Comparator.comparing(ActivityResponse::getCreatedAt))
                                        .collect(Collectors.toList());
                                if (childExtract.getCanHaveMultiple()) {
                                    complexChildSubInstances.addAll(responses);
                                } else {
                                    simpleChildSubInstances.addAll(responses);
                                }
                            }

                            if (!simpleChildSubInstances.isEmpty()) {
                                simpleChildInstances.add(new ActivityResponseMapping(childActivityCode, simpleChildSubInstances));
                            }

                            if (!complexChildSubInstances.isEmpty()) {
                                complexChildInstances.add(new ActivityResponseMapping(childActivityCode, complexChildSubInstances));
                            }
                        }

                        List<ActivityResponseMapping> childInstances = new ArrayList<>(simpleChildInstances);
                        childInstances.addAll(complexChildInstances);

                        instances.put(r, childInstances);
                    }

                    int numInstancesProcessed = 0;
                    for (var mainInstance : instances.keySet()) {
                        Map<String, String> subs = pt.getActivityInstanceSubstitutions(mainInstance.getId());
                        row.addAll(attributesCollector.format(subs));
                        row.addAll(responseCollector.getMainCollector().format(mainInstance, supplier, ""));
                        numInstancesProcessed++;

                        List<ActivityResponseMapping> childInstances = instances.get(mainInstance);
                        for (ActivityResponseMapping childSubInstances : childInstances) {
                            if (responseCollector.getChildCollectors().containsKey(childSubInstances.getActivityCode())) {
                                for (ActivityResponse childSubInstance : childSubInstances.getResponses()) {
                                    List<ActivityResponseCollector> childResponseCollectors =
                                            responseCollector.getChildCollectors().get(childSubInstances.getActivityCode());
                                    for (ActivityResponseCollector collector : childResponseCollectors) {
                                        row.addAll(collector.format(childSubInstance, supplier, ""));
                                    }
                                }
                            } else {
                                // Complex child
                                List<ComplexChildResponseCollector> complexChildResponseCollectors =
                                        responseCollector.getMultiChildCollectors().get(childSubInstances.getActivityCode());
                                List<Map<String, String>> complexChildValues = new ArrayList<>();
                                for (ActivityResponse childSubInstance : childSubInstances.getResponses()) {
                                    for (ComplexChildResponseCollector collector : complexChildResponseCollectors) {
                                        complexChildValues.add(collector.format(childSubInstance, supplier, ""));
                                    }
                                }
                                row.add(new Gson().toJson(complexChildValues));
                            }
                        }
                    }


                    while (numInstancesProcessed < maxInstances) {
                        row.addAll(attributesCollector.emptyRow());
                        row.addAll(responseCollector.getMainCollector().emptyRow());

                        Map<String, List<ActivityResponseCollector>> childResponseCollectors = responseCollector.getChildCollectors();
                        for (List<ActivityResponseCollector> responseCollectorList : childResponseCollectors.values()) {
                            for (ActivityResponseCollector collector : responseCollectorList) {
                                row.addAll(collector.emptyRow());
                            }
                        }

                        Map<String, List<ComplexChildResponseCollector>> complexChildResponseCollectors =
                                responseCollector.getMultiChildCollectors();
                        for (List<ComplexChildResponseCollector> complexChildResponseCollectorList :
                                complexChildResponseCollectors.values()) {
                            for (int i = 0; i < complexChildResponseCollectorList.size(); i++) {
                                row.add("");
                            }
                        }

                        numInstancesProcessed++;
                    }
                }
            } catch (Exception e) {
                String participantGuid = pt.getUser().getGuid();
                String studyGuid = pt.getStatus().getStudyGuid();
                log.error("Error while formatting data into csv for participant {} and study {}, skipping",
                        participantGuid, studyGuid, e);
                continue;
            }

            writer.writeNext(row.toArray(new String[]{}), false);
            numWritten += 1;

            log.info("[export] ({}) participant {} for study {}:"
                            + " status={}, hasProfile={}, hasAddress={}, numProviders={}, numInstances={}",
                    numWritten, pt.getUser().getGuid(), studyDto.getGuid(),
                    pt.getStatus().getEnrollmentStatus(), pt.getUser().hasProfile(), pt.getUser().hasAddress(),
                    pt.getProviders().size(), pt.getAllResponses().size());
        }

        writer.flush();
        return numWritten;
    }


}
