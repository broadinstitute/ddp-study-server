package org.broadinstitute.ddp.customexport.export;

import java.io.IOException;
import java.io.Writer;
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

import com.opencsv.CSVWriter;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.customexport.constants.CustomExportConfigFile;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.export.ActivityExtract;
import org.broadinstitute.ddp.export.ComponentDataSupplier;
import org.broadinstitute.ddp.export.ExportUtil;
import org.broadinstitute.ddp.export.collectors.ActivityAttributesCollector;
import org.broadinstitute.ddp.export.collectors.ActivityResponseCollector;
import org.broadinstitute.ddp.export.collectors.ParticipantMetadataFormatter;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.study.Participant;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomExporter {

    private static final Logger LOG = LoggerFactory.getLogger(CustomExporter.class);

    // A cache for user auth0 emails, storing (auth0UserId -> email).
    private static final Map<String, String> emailStore = new HashMap<>();

    private final Config cfg;
    private final String customGuid;
    private final String customActivity;

    public CustomExporter(Config cfg) {
        this.cfg = cfg;
        this.customActivity = cfg.getString(CustomExportConfigFile.ACTIVITY);
        this.customGuid = cfg.getString(CustomExportConfigFile.STUDY_GUID);
    }


    static List<Participant> extractParticipantDataSetByIds(Handle handle, StudyDto studyDto, Set<Long> userIds) {
        return ExportUtil.extractParticipantDataSetByIds(handle, studyDto, userIds, emailStore);
    }

    public static void clearCachedAuth0Emails() {
        ExportUtil.clearCachedAuth0Emails(emailStore);
    }

    /**
     * Extract all versions of an activity for a study
     * @param handle the database handle
     * @param activityDto the activity
     * @param studyGuid the study guid
     * @return list of extracts, in ascending order by version
     */
    private List<ActivityExtract> extractVersionsOfActivity(Handle handle, ActivityDto activityDto, String studyGuid,
                                                            List<String> excludedVersions) {
        JdbiActivityVersion jdbiActivityVersion = handle.attach(JdbiActivityVersion.class);
        FormActivityDao formActivityDao = handle.attach(FormActivityDao.class);
        ActivityDefStore store = ActivityDefStore.getInstance();
        List<ActivityExtract> activities = new ArrayList<>();

        String activityCode = activityDto.getActivityCode();
        List<ActivityVersionDto> versionDtos = jdbiActivityVersion.findAllVersionsInAscendingOrder(activityDto.getActivityId());
        if (excludedVersions != null) {
            versionDtos = versionDtos.stream().filter(v -> !excludedVersions.contains(v.getVersionTag())).collect(Collectors.toList());
        }

        for (ActivityVersionDto versionDto : versionDtos) {
            // Only supports form activities for now.
            FormActivityDef def = store.getActivityDef(studyGuid, activityCode, versionDto.getVersionTag());
            if (def == null) {
                def = formActivityDao.findDefByDtoAndVersion(activityDto, versionDto);
                store.setActivityDef(studyGuid, activityCode, versionDto.getVersionTag(), def);
            }
            activities.add(new ActivityExtract(def, versionDto));
        }

        return activities;
    }

    /**
     * Extracts all versions of custom export's activity
     * @param handle the database handle
     * @return list of extracts
     */
    public List<ActivityExtract> extractCustomActivity(Handle handle) {
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);

        Optional<ActivityDto> activityDtoOptional = jdbiActivity.findActivityByStudyGuidAndCode(customGuid, customActivity);
        if (activityDtoOptional.isEmpty()) {
            LOG.error("Activity {} DTO not found for custom export", customActivity);
            return null;
        }
        ActivityDto activityDto = activityDtoOptional.get();
        List<ActivityExtract> activities = extractVersionsOfActivity(handle, activityDto, customGuid,
                cfg.getStringList(CustomExportConfigFile.EXCLUDED_ACTIVITY_VERSIONS));

        LOG.info("Custom export found {} versions of activity {}", activities.size(), customActivity);

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
    public int exportDataSetAsCsv(StudyDto studyDto, List<ActivityExtract> activities, Iterator<Participant> participants,
                                  Writer output) throws IOException {
        ParticipantMetadataFormatter participantMetaFmt;


        List<String> excludedFields = new ArrayList<>(cfg.getStringList(CustomExportConfigFile.EXCLUDED_PARTICIPANT_FIELDS));
        participantMetaFmt = new ParticipantMetadataFormatter(excludedFields);


        List<String> headers = new LinkedList<>(participantMetaFmt.headers());

        Map<String, Integer> activityTagToNormalizedMaxInstanceCounts = new HashMap<>();
        Map<String, ActivityResponseCollector> responseCollectors = new HashMap<>();
        Map<String, ActivityAttributesCollector> attributesCollectors = new HashMap<>();
        for (ActivityExtract activity : activities) {
            Integer maxInstances = activity.getMaxInstancesSeen();
            if (maxInstances == null || maxInstances < 1) {
                LOG.warn("Found max instances count {} for activity tag {}, defaulting to 1", maxInstances, activity.getTag());
                // NOTE: default to one so we always have one set of columns even if no participant has an instance.
                maxInstances = 1;
            }

            List<String> firstFields;

            firstFields = cfg.getStringList(CustomExportConfigFile.FIRST_FIELDS);
            excludedFields = cfg.getStringList(CustomExportConfigFile.EXCLUDED_ACTIVITY_FIELDS);

            ActivityResponseCollector responseCollector = new ActivityResponseCollector(activity.getDefinition(), firstFields,
                    excludedFields);
            ActivityAttributesCollector attributesCollector;
            attributesCollector = new ActivityAttributesCollector(activity.getAttributesSeen(firstFields, excludedFields));

            activityTagToNormalizedMaxInstanceCounts.put(activity.getTag(), maxInstances);
            responseCollectors.put(activity.getTag(), responseCollector);
            attributesCollectors.put(activity.getTag(), attributesCollector);

            for (var i = 1; i <= maxInstances; i++) {
                headers.addAll(attributesCollector.headers());
                headers.addAll(responseCollector.getHeaders());
            }
        }

        CSVWriter writer = new CSVWriter(output);
        writer.writeNext(headers.toArray(new String[] {}), false);

        int numWritten = 0;
        while (participants.hasNext()) {
            Participant pt = participants.next();
            List<String> row;
            try {
                row = new LinkedList<>(participantMetaFmt.format(pt.getStatus(), pt.getUser()));
                ComponentDataSupplier supplier = new ComponentDataSupplier(pt.getUser().getAddress(), pt.getProviders());
                for (ActivityExtract activity : activities) {
                    String activityTag = activity.getTag();
                    int maxInstances = activityTagToNormalizedMaxInstanceCounts.get(activityTag);
                    ActivityResponseCollector responseCollector = responseCollectors.get(activityTag);
                    ActivityAttributesCollector attributesCollector = attributesCollectors.get(activityTag);

                    List<ActivityResponse> instances = pt.getResponses(activityTag).stream()
                            .sorted(Comparator.comparing(ActivityResponse::getCreatedAt))
                            .collect(Collectors.toList());

                    int numInstancesProcessed = 0;
                    for (var instance : instances) {
                        Map<String, String> subs = pt.getActivityInstanceSubstitutions(instance.getId());
                        row.addAll(attributesCollector.format(subs));
                        row.addAll(responseCollector.format(instance, supplier, ""));
                        numInstancesProcessed++;
                    }

                    while (numInstancesProcessed < maxInstances) {
                        row.addAll(attributesCollector.emptyRow());
                        row.addAll(responseCollector.emptyRow());
                        numInstancesProcessed++;
                    }
                }
            } catch (Exception e) {
                String participantGuid = pt.getUser().getGuid();
                String studyGuid = pt.getStatus().getStudyGuid();
                LOG.error("Error while formatting data into csv for participant {} and study {}, skipping",
                        participantGuid, studyGuid, e);
                continue;
            }

            writer.writeNext(row.toArray(new String[] {}), false);
            numWritten += 1;

            LOG.info("[export] ({}) participant {} for study {}:"
                            + " status={}, hasProfile={}, hasAddress={}, numProviders={}, numInstances={}",
                    numWritten, pt.getUser().getGuid(), studyDto.getGuid(),
                    pt.getStatus().getEnrollmentStatus(), pt.getUser().hasProfile(), pt.getUser().hasAddress(),
                    pt.getProviders().size(), pt.getAllResponses().size());
        }

        writer.flush();
        return numWritten;
    }


}
