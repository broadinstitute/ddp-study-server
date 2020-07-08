package org.broadinstitute.ddp.export;

import static org.broadinstitute.ddp.model.activity.types.ComponentType.MAILING_ADDRESS;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.opencsv.CSVWriter;
import com.typesafe.config.Config;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.ParticipantDao;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.export.collectors.ActivityMetadataCollector;
import org.broadinstitute.ddp.export.collectors.ActivityResponseCollector;
import org.broadinstitute.ddp.export.collectors.MailingAddressFormatter;
import org.broadinstitute.ddp.export.collectors.MedicalProviderFormatter;
import org.broadinstitute.ddp.export.collectors.ParticipantMetadataFormatter;
import org.broadinstitute.ddp.export.json.structured.ActivityInstanceRecord;
import org.broadinstitute.ddp.export.json.structured.ComponentQuestionRecord;
import org.broadinstitute.ddp.export.json.structured.CompositeQuestionRecord;
import org.broadinstitute.ddp.export.json.structured.DateQuestionRecord;
import org.broadinstitute.ddp.export.json.structured.DsmComputedRecord;
import org.broadinstitute.ddp.export.json.structured.ParticipantProfile;
import org.broadinstitute.ddp.export.json.structured.ParticipantRecord;
import org.broadinstitute.ddp.export.json.structured.PdfConfigRecord;
import org.broadinstitute.ddp.export.json.structured.PicklistQuestionRecord;
import org.broadinstitute.ddp.export.json.structured.QuestionRecord;
import org.broadinstitute.ddp.export.json.structured.SimpleQuestionRecord;
import org.broadinstitute.ddp.export.json.structured.UserRecord;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.study.Participant;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.ConsentService;
import org.broadinstitute.ddp.service.MedicalRecordService;
import org.broadinstitute.ddp.service.OLCService;
import org.broadinstitute.ddp.service.PdfService;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataExporter {

    public static final String TIMESTAMP_PATTERN = "MM/dd/yyyy HH:mm:ss";
    public static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter
            .ofPattern(TIMESTAMP_PATTERN).withZone(ZoneOffset.UTC);

    private static final Logger LOG = LoggerFactory.getLogger(DataExporter.class);
    private static final String REQUEST_TYPE = "_doc";

    // A cache for user auth0 emails, storing (auth0UserId -> email).
    private static Map<String, String> emailStore = new HashMap<>();

    private Config cfg;
    private Gson gson;
    private Set<String> componentNames;
    private PdfService pdfService;

    public static String makeExportCSVFilename(String studyGuid, Instant timestamp) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").withZone(ZoneOffset.UTC);
        return String.format("%s_%s.csv", studyGuid, fmt.format(timestamp));
    }

    public static void clearCachedAuth0Emails() {
        emailStore.clear();
    }

    public static void evictCachedAuth0Emails(Set<String> auth0UserIds) {
        if (CollectionUtils.isNotEmpty(auth0UserIds)) {
            auth0UserIds.forEach(emailStore::remove);
        }
    }

    public static Map<String, String> fetchAndCacheAuth0Emails(Handle handle, String studyGuid, Set<String> auth0UserIds) {
        var mgmtClient = Auth0Util.getManagementClientForStudy(handle, studyGuid);
        Map<String, String> emailResults = new Auth0Util(mgmtClient.getDomain())
                .getUserPassConnEmailsByAuth0UserIds(auth0UserIds, mgmtClient.getToken());
        emailResults.forEach((auth0UserId, email) -> emailStore.put(auth0UserId, email));
        return emailResults;
    }

    public DataExporter(Config cfg) {
        this.cfg = cfg;
        this.gson = new GsonBuilder().serializeNulls().create();
        this.pdfService = new PdfService();
        componentNames = new HashSet<>();
        componentNames.add("MAILING_ADDRESS");
        componentNames.add("INITIAL_BIOPSY");
        componentNames.add("INSTITUTION");
        componentNames.add("PHYSICIAN");
    }

    /**
     * Extract all the activities for a study, including the different versions of each activity.
     *
     * @param handle   the database handle
     * @param studyDto the study
     * @return list of extracts, in ascending order by activity display order and version
     */
    public List<ActivityExtract> extractActivities(Handle handle, StudyDto studyDto) {
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        JdbiActivityVersion jdbiActivityVersion = handle.attach(JdbiActivityVersion.class);
        FormActivityDao formActivityDao = handle.attach(FormActivityDao.class);

        ActivityDefStore store = ActivityDefStore.getInstance();
        List<ActivityExtract> activities = new ArrayList<>();
        String studyGuid = studyDto.getGuid();

        for (ActivityDto activityDto : jdbiActivity.findOrderedDtosByStudyId(studyDto.getId())) {
            String activityCode = activityDto.getActivityCode();
            List<ActivityVersionDto> versionDtos = jdbiActivityVersion.findAllVersionsInAscendingOrder(activityDto.getActivityId());
            for (ActivityVersionDto versionDto : versionDtos) {
                // Only supports form activities for now.
                FormActivityDef def = store.getActivityDef(studyGuid, activityCode, versionDto.getVersionTag());
                if (def == null) {
                    def = formActivityDao.findDefByDtoAndVersion(activityDto, versionDto);
                    store.setActivityDef(studyGuid, activityCode, versionDto.getVersionTag(), def);
                }
                activities.add(new ActivityExtract(def, versionDto));
            }
        }

        LOG.info("[export] found {} activities for study {}", activities.size(), studyGuid);

        return activities;
    }

    public List<Participant> extractParticipantDataSet(Handle handle, StudyDto studyDto) {
        return extractParticipantDataSetByIds(handle, studyDto, null);
    }

    public List<Participant> extractParticipantDataSetByIds(Handle handle, StudyDto studyDto, Set<Long> userIds) {
        Stream<Participant> resultset;
        if (userIds == null) {
            resultset = handle.attach(ParticipantDao.class).findParticipantsWithFullData(studyDto.getId());
        } else {
            resultset = handle.attach(ParticipantDao.class).findParticipantsWithFullDataByUserIds(studyDto.getId(), userIds);
        }
        return extractParticipantsFromResultSet(handle, studyDto, resultset);
    }

    public List<Participant> extractParticipantDataSetByGuids(Handle handle, StudyDto studyDto, Set<String> userGuids) {
        Stream<Participant> resultset;
        if (userGuids == null) {
            resultset = handle.attach(ParticipantDao.class).findParticipantsWithFullData(studyDto.getId());
        } else {
            resultset = handle.attach(ParticipantDao.class).findParticipantsWithFullDataByUserGuids(studyDto.getId(), userGuids);
        }
        return extractParticipantsFromResultSet(handle, studyDto, resultset);
    }

    private List<Participant> extractParticipantsFromResultSet(Handle handle, StudyDto studyDto, Stream<Participant> resultset) {
        Map<String, String> usersMissingEmails = new HashMap<>();

        Map<String, Participant> participants = resultset
                .peek(pt -> {
                    String auth0UserId = pt.getUser().getAuth0UserId();
                    if (StringUtils.isBlank(auth0UserId)) {
                        return;
                    }
                    String email = emailStore.get(auth0UserId);
                    if (email == null) {
                        usersMissingEmails.put(auth0UserId, pt.getUser().getGuid());
                    } else {
                        pt.getUser().setEmail(email);
                    }
                })
                .collect(Collectors.toMap(pt -> pt.getUser().getGuid(), pt -> pt));

        if (!usersMissingEmails.isEmpty()) {
            fetchAndCacheAuth0Emails(handle, studyDto.getGuid(), usersMissingEmails.keySet())
                    .forEach((auth0UserId, email) -> participants.get(usersMissingEmails.get(auth0UserId)).getUser().setEmail(email));
        }

        ArrayList<Participant> dataset = new ArrayList<>(participants.values());
        LOG.info("[export] extracted {} participants for study {}", dataset.size(), studyDto.getGuid());
        return dataset;
    }

    /**
     * Extract all the participants for a study, pooling together all the data associated with the participant.
     *
     * @param handle                   the database handle
     * @param studyDto                 the study
     * @param participantGuids         participants to export data for: null for all, empty for zero, 1+ for specific
     * @param exportStructuredDocument defines the type of the ES document to be created
     */
    public void exportParticipantsToElasticsearchByGuids(
            Handle handle,
            StudyDto studyDto,
            Set<String> participantGuids,
            boolean exportStructuredDocument
    ) {
        List<ActivityExtract> activityExtracts = extractActivities(handle, studyDto);
        List<Participant> participants = extractParticipantDataSetByGuids(handle, studyDto, participantGuids);
        exportToElasticsearch(handle, studyDto, activityExtracts, participants, exportStructuredDocument);
    }

    public void exportParticipantsToElasticsearchByIds(Handle handle, StudyDto studyDto, Set<Long> participantIds,
                                                       boolean exportStructuredDocument) {
        List<ActivityExtract> activityExtracts = extractActivities(handle, studyDto);
        List<Participant> participants = extractParticipantDataSetByIds(handle, studyDto, participantIds);
        exportToElasticsearch(handle, studyDto, activityExtracts, participants, exportStructuredDocument);
    }

    public void exportToElasticsearch(Handle handle, StudyDto studyDto,
                                       List<ActivityExtract> activities,
                                       List<Participant> participants,
                                       boolean exportStructuredDocument) {
        int maxExtractSize = cfg.getInt(ConfigFile.ELASTICSEARCH_EXPORT_BATCH_SIZE);

        if (!exportStructuredDocument) {
            OLCPrecision precision = studyDto.getOlcPrecision() == null ? OLCService.DEFAULT_OLC_PRECISION : studyDto.getOlcPrecision();
            for (Participant participant : participants) {
                MailAddress address = participant.getUser().getAddress();
                if (address != null) {
                    address.setPlusCode(OLCService.convertPlusCodeToPrecision(address.getPlusCode(), precision));
                }
            }
        }

        List<Participant> batch = new ArrayList<>();
        Iterator<Participant> iter = participants.iterator();
        int exportsSoFar = 0;
        while (iter.hasNext()) {
            Participant participant = iter.next();
            try {
                batch.add(participant);
                if (batch.size() == maxExtractSize || !iter.hasNext()) {
                    int extractSize = batch.size();
                    LOG.info("Exporting " + extractSize + " elasticsearch records");

                    convertInfoToJSONAndExportToES(
                            handle,
                            activities,
                            batch,
                            studyDto,
                            exportStructuredDocument
                    );
                    exportsSoFar += extractSize;
                    batch.clear();

                    LOG.info("Have now exported {} participants out of {} for study: {}.",
                            exportsSoFar, participants.size(), studyDto.getGuid());
                }
            } catch (Exception e) {
                LOG.error("[export] failed to export participant {} for study {}, continuing",
                        participant.getUser().getGuid(), studyDto.getGuid(), e);
            }
        }
    }

    public List<ActivityExtract> exportActivityDefinitionsToElasticsearch(
            Handle handle, StudyDto studyDto, Config cfg) {

        //get study activities
        List<ActivityExtract> activityExtracts = extractActivities(handle, studyDto);

        Map<String, Object> allActivityDefs = new HashMap<>();
        //Object is List<Map<String, Object>>

        for (ActivityExtract activity : activityExtracts) {
            String activityName = activity.getDefinition().getTranslatedNames().stream()
                    .filter(name -> name.getLanguageCode().equals(LanguageStore.DEFAULT_LANG_CODE))
                    .map(Translation::getText)
                    .findFirst()
                    .orElse("");
            ActivityResponseCollector formatter = new ActivityResponseCollector(activity.getDefinition());
            Map<String, Object> activityDefinitions = new HashMap<>();
            activityDefinitions.put("studyGuid", studyDto.getGuid());
            activityDefinitions.put("activityCode", activity.getDefinition().getActivityCode());
            activityDefinitions.put("activityName", activityName);
            activityDefinitions.put("activityVersion", activity.getDefinition().getVersionTag());
            activityDefinitions.putAll(formatter.questionDefinitions());

            allActivityDefs.put(activity.getTag(), activityDefinitions);
        }

        String index = ElasticsearchServiceUtil.getIndexForStudy(
                handle,
                studyDto,
                ElasticSearchIndexType.ACTIVITY_DEFINITION
        );

        try {
            exportDataToElasticSearch(index, allActivityDefs, cfg);
        } catch (IOException e) {
            LOG.error("[activitydefinition export] failed during export ", e);
        }

        return activityExtracts;
    }

    private void exportDataToElasticSearch(String index, Map<String, Object> data, Config cfg) throws IOException {
        if (data.isEmpty()) {
            return;
        }
        try (RestHighLevelClient client = ElasticsearchServiceUtil.getClientForElasticsearchCloud(cfg)) {
            BulkRequest bulkRequest = new BulkRequest().timeout("2m");

            data.forEach((key, value) -> {
                String esDoc = gson.toJson(value);
                UpdateRequest updateRequest = new UpdateRequest()
                        .index(index)
                        .type(REQUEST_TYPE)
                        .id(key)
                        .doc(esDoc, XContentType.JSON)
                        .docAsUpsert(true);
                bulkRequest.add(updateRequest);
            });

            BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);

            if (bulkResponse.hasFailures()) {
                LOG.error(bulkResponse.buildFailureMessage());
            }
        }
    }

    public void exportUsersToElasticsearch(Handle handle, StudyDto studyDto, Set<Long> userIds) {

        //load participants
        Stream<Participant> resultset;
        if (userIds != null) {
            resultset = handle.attach(ParticipantDao.class).findParticipantsWithUserProfileByStudyIdAndUserIds(
                    studyDto.getId(), userIds);
        } else {
            resultset = handle.attach(ParticipantDao.class)
                    .findParticipantsWithUserProfileByStudyId(studyDto.getId());
        }
        List<Participant> participants = extractParticipantsFromResultSet(handle, studyDto, resultset);

        //load governances and build data structures to get proxies and governedUsers
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        Stream<Governance> allGovernances = userGovernanceDao.findActiveGovernancesByStudyGuid(studyDto.getGuid());
        Map<String, Set<String>> proxiesMap = new HashMap<>();
        Map<String, Set<String>> governedUsersMap = new HashMap<>();
        Set<String> operatorGuids = new HashSet<>();

        allGovernances.forEach(governance -> {
            String proxyGuid = governance.getProxyUserGuid();
            String governedUserGuid = governance.getGovernedUserGuid();
            Long governedUserId = governance.getGovernedUserId();
            governedUsersMap.computeIfAbsent(governedUserGuid, key -> new HashSet<>());
            governedUsersMap.get(governedUserGuid).add(proxyGuid);
            proxiesMap.computeIfAbsent(proxyGuid, key -> new HashSet<>());
            proxiesMap.get(proxyGuid).add(governedUserGuid);
            if (userIds == null || userIds.contains(governedUserId)) {
                operatorGuids.add(proxyGuid);
            }
        });

        Map<String, Object> allUsers = new HashMap<>();
        //load participants
        participants.forEach(participant -> {
            User user = participant.getUser();
            allUsers.put(user.getGuid(), createUserRecord(user, proxiesMap, governedUsersMap));
        });

        //load operators
        Stream<User> users = handle.attach(UserDao.class).findUsersAndProfilesByGuids(operatorGuids);
        List<User> operators = extractUsersFromResultSet(handle, studyDto, users);
        operators.forEach(user -> {
            if (allUsers.containsKey(user.getGuid())) {
                //operator/user is also a participant... skip
                return;
            }
            allUsers.put(user.getGuid(), createUserRecord(user, proxiesMap, governedUsersMap));
        });

        String index = ElasticsearchServiceUtil.getIndexForStudy(
                handle,
                studyDto,
                ElasticSearchIndexType.USERS
        );

        try {
            exportDataToElasticSearch(index, allUsers, cfg);
        } catch (IOException e) {
            LOG.error("[users elasticsearch export] failed during export ", e);
        }
    }

    private UserRecord createUserRecord(User user,
                                        Map<String, Set<String>> proxiesMap,
                                        Map<String, Set<String>> governedUsersMap) {
        UserProfile userProfile = user.getProfile();
        if (userProfile == null) {
            userProfile = new UserProfile.Builder(user.getId()).build();
        }
        ParticipantProfile profile = new ParticipantProfile(userProfile.getFirstName(), userProfile.getLastName(),
                user.getGuid(), user.getHruid(), user.getLegacyAltPid(), user.getLegacyShortId(), user.getEmail(),
                userProfile.getPreferredLangCode(), userProfile.getDoNotContact(), user.getCreatedAt());

        Set<String> proxies = new HashSet<>();
        Set<String> governedUsers = new HashSet<>();
        if (governedUsersMap.containsKey(user.getGuid())) {
            proxies = governedUsersMap.get(user.getGuid());
        }
        if (proxiesMap.containsKey(user.getGuid())) {
            governedUsers = proxiesMap.get(user.getGuid());
        }

        return new UserRecord(profile, proxies, governedUsers);
    }

    private List<User> extractUsersFromResultSet(Handle handle, StudyDto studyDto, Stream<User> resultset) {
        Map<String, String> usersMissingEmails = new HashMap<>();

        Map<String, User> users = resultset
                .peek(user -> {
                    String auth0UserId = user.getAuth0UserId();
                    if (StringUtils.isBlank(auth0UserId)) {
                        return;
                    }
                    String email = emailStore.get(auth0UserId);
                    if (email == null) {
                        usersMissingEmails.put(auth0UserId, user.getGuid());
                    } else {
                        user.setEmail(email);
                    }
                })
                .collect(Collectors.toMap(user -> user.getGuid(), user -> user));

        if (!usersMissingEmails.isEmpty()) {
            fetchAndCacheAuth0Emails(handle, studyDto.getGuid(), usersMissingEmails.keySet())
                    .forEach((auth0UserId, email) -> users.get(usersMissingEmails.get(auth0UserId)).setEmail(email));
        }

        return new ArrayList<>(users.values());
    }

    /**
     * Exports data for a given study to Elasticsearch in the form of an upsert.
     *
     * @param handle the database handle
     */
    private void convertInfoToJSONAndExportToES(Handle handle,
                                                List<ActivityExtract> activities,
                                                List<Participant> dataset,
                                                StudyDto studyDto,
                                                boolean exportStructuredDocument
    ) throws IOException {
        String index = ElasticsearchServiceUtil.getIndexForStudy(
                handle,
                studyDto,
                exportStructuredDocument ? ElasticSearchIndexType.PARTICIPANTS_STRUCTURED : ElasticSearchIndexType.PARTICIPANTS
        );

        List<PdfConfigInfo> studyPdfConfigs = handle.attach(PdfDao.class).findConfigInfoByStudyGuid(studyDto.getGuid());

        Map<Long, List<PdfVersion>> configPdfVersions = new HashMap<>();
        for (PdfConfigInfo pdfConfigInfo : studyPdfConfigs) {
            if (!configPdfVersions.containsKey(pdfConfigInfo.getId())) {
                List<PdfVersion> versions = handle.attach(PdfDao.class).findOrderedConfigVersionsByConfigId(pdfConfigInfo.getId());
                if (versions.isEmpty()) {
                    throw new DaoException("No versions found for pdf config with id=" + pdfConfigInfo.getId() + ", need at least one");
                } else {
                    configPdfVersions.put(pdfConfigInfo.getId(), versions);
                }
            }
        }

        //load proxies
        Map<String, List<Governance>> userProxies = handle.attach(UserGovernanceDao.class)
                .findActiveGovernancesByStudyGuid(studyDto.getGuid())
                .collect(Collectors.groupingBy(Governance::getGovernedUserGuid));

        Map<String, List<String>> participantProxyGuids = new HashMap<>();
        if (!userProxies.isEmpty()) {
            userProxies.forEach((key, value) -> {
                List<String> proxyGuids = new ArrayList<>();
                value.forEach(governance -> proxyGuids.add(governance.getProxyUserGuid()));
                participantProxyGuids.put(key, proxyGuids);
            });
        }

        MedicalRecordService medicalRecordService = new MedicalRecordService(ConsentService.createInstance());
        GovernancePolicy governancePolicy = handle.attach(StudyGovernanceDao.class)
                .findPolicyByStudyId(studyDto.getId()).orElse(null);

        enrichWithDSMEventDates(handle, medicalRecordService, governancePolicy, studyDto.getId(), dataset);

        List<InvitationDto> invitations = handle.attach(InvitationDao.class)
                .findAllInvitations(studyDto.getId());
        StudyExtract studyExtract = new StudyExtract(activities,
                studyPdfConfigs,
                configPdfVersions,
                participantProxyGuids,
                invitations);

        Map<String, String> participantRecords = prepareParticipantRecordsForJSONExport(
                studyExtract, dataset, exportStructuredDocument, handle, medicalRecordService);

        try (RestHighLevelClient client = ElasticsearchServiceUtil.getClientForElasticsearchCloud(cfg)) {
            BulkRequest bulkRequest = new BulkRequest().timeout("2m");

            participantRecords.forEach((key, value) -> {
                UpdateRequest updateRequest = new UpdateRequest()
                        .index(index)
                        .type(REQUEST_TYPE)
                        .id(key)
                        .doc(value, XContentType.JSON)
                        .docAsUpsert(true);
                bulkRequest.add(updateRequest);
            });

            BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);

            if (bulkResponse.hasFailures()) {
                LOG.error(bulkResponse.buildFailureMessage());
            }
        }
    }

    void enrichWithDSMEventDates(Handle handle,
                                 MedicalRecordService medicalRecordService,
                                 GovernancePolicy governancePolicy,
                                 long studyId,
                                 List<Participant> dataset) {

        PexInterpreter pexInterpreter = new TreeWalkInterpreter();

        dataset.forEach(participant -> {
            DateValue diagnosisDate = medicalRecordService
                    .getDateOfDiagnosis(handle, participant.getUser().getId(), studyId).orElse(null);

            // First grab from the profile, if it's not there then look in activity data using medical-record service
            LocalDate birthDate = Optional.ofNullable(participant.getUser().getProfile())
                    .map(UserProfile::getBirthDate)
                    .or(() -> medicalRecordService
                            .getDateOfBirth(handle, participant.getUser().getId(), studyId)
                            .flatMap(DateValue::asLocalDate))
                    .orElse(null);

            LocalDate dateOfMajority = null;
            if (governancePolicy != null) {
                AgeOfMajorityRule aomRule = governancePolicy.getApplicableAgeOfMajorityRule(handle,
                        pexInterpreter,
                        participant.getUser().getGuid())
                        .orElse(null);

                if (birthDate != null && aomRule != null) {
                    dateOfMajority = aomRule.getDateOfMajority(birthDate);
                }
            }

            participant.setBirthDate(birthDate);
            participant.setDateOfDiagnosis(diagnosisDate);
            participant.setDateOfMajority(dateOfMajority);
        });
    }

    /**
     * Convert the given dataset to the given output using JSON formatting.
     *
     * @param studyExtract study data with list of activity data and study pdf-config info
     * @param participants the participant data for a study
     * @return map with key = participant guid and value = participant records
     */
    public Map<String, String> prepareParticipantRecordsForJSONExport(
            StudyExtract studyExtract,
            List<Participant> participants,
            boolean exportStructuredDocument,
            Handle handle,
            MedicalRecordService medicalRecordService
    ) {
        Map<String, String> participantsRecords = new HashMap<>();
        for (Participant extract : participants) {
            try {
                String elasticSearchDocument = null;
                if (exportStructuredDocument) {
                    elasticSearchDocument = formatParticipantToStructuredJSON(studyExtract,
                            extract,
                            handle,
                            medicalRecordService);
                } else {
                    elasticSearchDocument = formatParticipantToFlatJSON(studyExtract.getActivities(), extract);
                }
                participantsRecords.put(extract.getUser().getGuid(), elasticSearchDocument);
            } catch (Exception e) {
                String participantGuid = extract.getStatus().getUserGuid();
                String studyGuid = extract.getStatus().getStudyGuid();
                LOG.error("Error while formatting data into {} json for participant {} and study {}, skipping",
                        exportStructuredDocument ? "structured" : "flat", participantGuid, studyGuid, e);
            }
        }
        return participantsRecords;
    }

    /**
     * For a given participant, setup their information in the correct format for export.
     *
     * @param activities the list of activity data for a study
     * @param extract    the participant data for the study
     * @return
     */
    private String formatParticipantToFlatJSON(List<ActivityExtract> activities,
                                               Participant extract) {
        ParticipantMetadataFormatter participantMetaFmt = new ParticipantMetadataFormatter();
        ActivityMetadataCollector activityMetadataCollector = new ActivityMetadataCollector();
        Map<String, ActivityResponseCollector> responseCollectors = new HashMap<>();
        for (ActivityExtract activity : activities) {
            ActivityResponseCollector formatter = new ActivityResponseCollector(activity.getDefinition());
            responseCollectors.put(activity.getTag(), formatter);
        }

        Map<String, String> recordForParticipant = new LinkedHashMap<>();
        recordForParticipant.putAll(participantMetaFmt.records(extract.getStatus(), extract.getUser()));

        ComponentDataSupplier supplier = new ComponentDataSupplier(extract.getUser().getAddress(), extract.getProviders());
        for (ActivityExtract activity : activities) {
            ActivityResponseCollector activityResponseCollector = responseCollectors.get(activity.getTag());
            List<ActivityResponse> instances = extract.getResponses(activity.getTag());
            if (!instances.isEmpty()) {
                if (instances.size() > 1) {
                    LOG.warn("[export] participant {} has {} instances of activity {} {}, will only export the latest one",
                            extract.getUser().getGuid(), instances.size(),
                            activity.getDefinition().getActivityCode(), activity.getDefinition().getVersionTag());
                }
                ActivityResponse instance = instances.stream()
                        .max(Comparator.comparing(ActivityResponse::getCreatedAt))
                        .get();
                recordForParticipant.putAll(activityMetadataCollector.records(activity.getTag(), instance));
                recordForParticipant.putAll(activityResponseCollector.records(instance, supplier, null));
            } else {
                recordForParticipant.putAll(activityMetadataCollector.emptyRecord(activity.getTag()));
                recordForParticipant.putAll(activityResponseCollector.emptyRecord(null));
            }
        }

        return gson.toJson(recordForParticipant);
    }

    /**
     * Unlike formatParticipantToFlatJSON, creates a nested JSON with participant data
     */
    private String formatParticipantToStructuredJSON(
            StudyExtract studyExtract,
            Participant participant,
            Handle handle,
            MedicalRecordService medicalRecordService
    ) {
        EnrollmentStatusDto statusDto = participant.getStatus();
        User user = participant.getUser();

        // Profile
        ParticipantProfile.Builder builder = ParticipantProfile.builder();
        UserProfile userProfile = user.getProfile();
        if (userProfile != null) {
            builder.setFirstName(userProfile.getFirstName());
            builder.setLastName(userProfile.getLastName());
            builder.setPreferredLanguage(userProfile.getPreferredLangCode());
            builder.setDoNotContact(userProfile.getDoNotContact());
        }
        builder.setGuid(user.getGuid())
                .setHruid(user.getHruid())
                .setLegacyAltPid(user.getLegacyAltPid())
                .setLegacyShortId(user.getLegacyShortId())
                .setEmail(user.getEmail())
                .setCreatedAt(user.getCreatedAt());
        ParticipantProfile participantProfile = builder.build();

        // ActivityInstances (aka "surveys")
        List<ActivityInstanceRecord> activityInstanceRecords = new ArrayList<>();
        Map<String, Set<String>> userActivityVersions = new HashMap<>();
        for (ActivityExtract activityExtract : studyExtract.getActivities()) {
            List<ActivityResponse> instances = participant.getResponses(activityExtract.getTag());
            for (ActivityResponse instance : instances) {
                ActivityInstanceStatusDto lastStatus = instance.getLatestStatus();
                List<QuestionRecord> questionsAnswers = createQuestionRecordsForActivity(activityExtract.getDefinition(),
                        instance, participant);
                ActivityInstanceRecord activityInstanceRecord = new ActivityInstanceRecord(
                        instance.getActivityVersionTag(),
                        instance.getActivityCode(),
                        instance.getGuid(),
                        lastStatus.getType(),
                        instance.getCreatedAt(),
                        instance.getFirstCompletedAt(),
                        lastStatus.getUpdatedAt(),
                        questionsAnswers
                );

                activityInstanceRecords.add(activityInstanceRecord);
                if (lastStatus.getType().equals(InstanceStatusType.COMPLETE)) {
                    userActivityVersions
                            .computeIfAbsent(instance.getActivityCode(), key -> new HashSet<>())
                            .add(instance.getActivityVersionTag());
                }
            }
        }

        String userGuid = user.getGuid();
        String studyGuid = statusDto.getStudyGuid();

        List<PdfConfigInfo> pdfConfigInfoList = findPdfConfigsForStudyUser(
                studyExtract.getStudyPdfConfigs(), studyExtract.getPdfVersions(), userActivityVersions);
        List<PdfConfigRecord> pdfConfigRecords = new ArrayList<>();
        for (PdfConfigInfo info : pdfConfigInfoList) {
            pdfConfigRecords.add(new PdfConfigRecord(info.getConfigName(), info.getDisplayName()));
        }

        // Retrieving information to compute the dsm record
        MedicalRecordService.ParticipantConsents consents = medicalRecordService
                .fetchBloodAndTissueConsents(handle, user.getId(), userGuid, statusDto.getStudyId(), studyGuid);

        DsmComputedRecord dsmComputedRecord =
                new DsmComputedRecord(participant.getBirthDate(),
                        participant.getDateOfMajority(),
                        participant.getDateOfDiagnosis(),
                        consents.hasConsentedToBloodDraw(),
                        consents.hasConsentedToTissueSample(),
                        pdfConfigRecords);

        List<String> proxies = studyExtract.getParticipantProxyGuids().get(user.getGuid());
        if (proxies == null) {
            proxies = List.of();
        }
        List<InvitationDto> invitations = studyExtract.getInvitations().stream()
                .filter(invite -> invite.getUserId() != null && invite.getUserId().equals(user.getId()))
                .collect(Collectors.toList());
        ParticipantRecord participantRecord = new ParticipantRecord(
                statusDto.getEnrollmentStatus(),
                statusDto.getValidFromMillis(),
                participantProfile,
                activityInstanceRecords,
                participant.getProviders(),
                user.getAddress(),
                dsmComputedRecord,
                proxies,
                invitations
        );
        return gson.toJson(participantRecord);
    }

    private List<PdfConfigInfo> findPdfConfigsForStudyUser(List<PdfConfigInfo> studyConfigs,
                                                           Map<Long, List<PdfVersion>> configPdfVersions,
                                                           Map<String, Set<String>> userActivityVersions) {

        List<PdfConfigInfo> userPdfConfigs = new ArrayList<>();

        for (PdfConfigInfo pdfConfigInfo : studyConfigs) {
            PdfVersion pdfVersion = pdfService.findPdfConfigVersionForUser(
                    configPdfVersions.get(pdfConfigInfo.getId()),
                    userActivityVersions);
            if (pdfVersion != null) {
                userPdfConfigs.add(pdfConfigInfo);
            }
        }
        return userPdfConfigs;
    }

    /**
     * Traverse the activity definition, creating a record for each question that is answered, while flattening block structures.
     *
     * @param definition The activity definition
     * @param response   An activity instance with the answers
     * @return A flat list of question records
     */
    private List<QuestionRecord> createQuestionRecordsForActivity(ActivityDef definition, ActivityResponse response,
                                                                  Participant participant) {
        List<QuestionRecord> questionRecords = new ArrayList<>();

        if (definition.getActivityType() != ActivityType.FORMS) {
            return questionRecords;
        }

        List<QuestionDef> allQuestions = new ArrayList<>();
        Template dummyTemplate = Template.text("dummy");
        List<RuleDef> dummyValidations = new ArrayList<>();

        FormActivityDef formDef = (FormActivityDef) definition;
        // Enumerating all sections and their blocks and retrieving answers
        for (FormSectionDef formSection : formDef.getAllSections()) {
            for (FormBlockDef formBlock : formSection.getBlocks()) {
                // Step inside the group block and get questions from all nested question blocks
                // Link each nested question to the grouping block
                if (formBlock.getBlockType() == BlockType.GROUP) {
                    GroupBlockDef groupBlock = (GroupBlockDef) formBlock;
                    List<FormBlockDef> nestedBlocks = groupBlock.getNested();
                    for (FormBlockDef nestedBlock : nestedBlocks) {
                        if (nestedBlock.getBlockType() == BlockType.QUESTION) {
                            QuestionBlockDef questionBlock = (QuestionBlockDef) nestedBlock;
                            allQuestions.add(questionBlock.getQuestion());
                        }
                    }
                } else if (formBlock.getBlockType() == BlockType.CONDITIONAL) {
                    ConditionalBlockDef conditionalBlock = (ConditionalBlockDef) formBlock;
                    QuestionDef controlQuestion = conditionalBlock.getControl();
                    // Adding a control question itself
                    allQuestions.add(controlQuestion);
                    // Adding nested questions and linking them to the control question
                    for (FormBlockDef nestedBlock : conditionalBlock.getNested()) {
                        if (nestedBlock.getBlockType() == BlockType.QUESTION) {
                            QuestionBlockDef questionBlock = (QuestionBlockDef) nestedBlock;
                            allQuestions.add(questionBlock.getQuestion());
                        }
                    }
                } else if (formBlock.getBlockType() == BlockType.QUESTION) {
                    QuestionBlockDef questionBlock = (QuestionBlockDef) formBlock;
                    allQuestions.add(questionBlock.getQuestion());
                } else if (formBlock.getBlockType() == BlockType.COMPONENT) {
                    ComponentBlockDef componentBlockDef = (ComponentBlockDef) formBlock;
                    QuestionDef componentDef;
                    if (componentBlockDef.getComponentType() == MAILING_ADDRESS) {
                        componentDef = new CompositeQuestionDef(componentBlockDef.getComponentType().name(), false,
                                dummyTemplate, null, null, dummyValidations, null,
                                null, false, true, null, null, false);
                    } else {
                        componentDef = new CompositeQuestionDef(((PhysicianInstitutionComponentDef) componentBlockDef).getInstitutionType()
                                .name(), false, dummyTemplate, null, null, dummyValidations, null,
                                null, false, true, null, null, false);
                    }

                    allQuestions.add(componentDef);
                }
            }
        }

        FormResponse instance = (FormResponse) response;
        for (QuestionDef question : allQuestions) {
            if (question.getQuestionType() == QuestionType.COMPOSITE) {
                CompositeQuestionDef composite = (CompositeQuestionDef) question;
                if (componentNames.contains(composite.getStableId())) {
                    questionRecords.add(createRecordForComponent(composite.getStableId(), participant));
                    continue;
                }
                if (composite.shouldUnwrapChildQuestions() && instance.hasAnswer(composite.getStableId())) {
                    // There should be one row with one answer per child. Put them into the response object so we can recurse.
                    ((CompositeAnswer) instance.getAnswer(composite.getStableId())).getValue().stream()
                            .flatMap(row -> row.getValues().stream())
                            .forEach(instance::putAnswer);
                    composite.getChildren().forEach(child -> questionRecords.add(createRecordForQuestion(child, instance)));
                    continue;
                }
            }
            questionRecords.add(createRecordForQuestion(question, instance));
        }

        return questionRecords.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private QuestionRecord createRecordForComponent(String component, Participant participant) {
        ComponentQuestionRecord record = null;
        List<List<String>> values;
        switch (component) {
            case "MAILING_ADDRESS":
                MailAddress address = participant.getUser().getAddress();
                values = new MailingAddressFormatter().collectAsAnswer(address);
                if (CollectionUtils.isNotEmpty(values)) {
                    record = new ComponentQuestionRecord("MAILING_ADDRESS", values);
                }
                break;
            case "PHYSICIAN":
            case "INSTITUTION":
            case "INITIAL_BIOPSY":
                values = new MedicalProviderFormatter().collectAsAnswer(InstitutionType.valueOf(component), participant.getProviders());
                if (CollectionUtils.isNotEmpty(values)) {
                    record = new ComponentQuestionRecord(component, values);
                }
                break;
            default:
                throw new DDPException("Unhandled component type " + component);
        }
        return record;
    }

    /**
     * Given a question block, extracts a question with answers
     *
     * @param question A question definition to explore
     * @param instance The activity instance that has answers
     * @return A question record, or null if it's an unanswered deprecated question that should be skipped for export
     */
    private QuestionRecord createRecordForQuestion(QuestionDef question, FormResponse instance) {
        if (question.isDeprecated() && !instance.hasAnswer(question.getStableId())) {
            return null;
        }
        Answer answer = instance.getAnswer(question.getStableId());
        if (answer == null) {
            return null;
        }
        QuestionType type = answer.getQuestionType();
        if (type == QuestionType.DATE) {
            DateValue value = (DateValue) answer.getValue();
            return new DateQuestionRecord(question.getStableId(), value);
        } else if (answer.getQuestionType() == QuestionType.PICKLIST) {
            List<SelectedPicklistOption> selected = ((PicklistAnswer) answer).getValue();
            return new PicklistQuestionRecord(question.getStableId(), selected);
        } else if (answer.getQuestionType() == QuestionType.COMPOSITE) {
            List<AnswerRow> rows = ((CompositeAnswer) answer).getValue();
            return new CompositeQuestionRecord(question.getStableId(), rows);
        } else {
            return new SimpleQuestionRecord(type, question.getStableId(), answer.getValue());
        }
    }

    // Convenience method to export CSV with auto-generated filename in given directory.
    public int exportCsvToDirectory(Handle handle, StudyDto studyDto, Path directory) throws IOException {
        String filename = makeExportCSVFilename(studyDto.getGuid(), Instant.now());
        Path outPath = directory.resolve(filename);
        return exportCsvToFile(handle, studyDto, outPath);
    }

    // Convenience method to export CSV using given filename.
    public int exportCsvToFile(Handle handle, StudyDto studyDto, Path filename) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(filename);
        int numWritten = exportCsvToOutput(handle, studyDto, writer);
        writer.close();
        return numWritten;
    }

    /**
     * Export all participant data for study in CSV format to the given output. Caller is responsible for closing the given output writer.
     *
     * @param handle   the database handle
     * @param studyDto the study
     * @param output   the output writer to use
     * @return number of participant records written
     * @throws IOException if error while writing
     */
    public int exportCsvToOutput(Handle handle, StudyDto studyDto, Writer output) throws IOException {
        List<ActivityExtract> activities = extractActivities(handle, studyDto);
        List<Participant> dataset = extractParticipantDataSet(handle, studyDto);
        return exportDataSetAsCsv(studyDto, activities, dataset, output);
    }

    /**
     * Export the given dataset to the given output using CSV formatting. Caller is responsible for closing the given output writer.
     *
     * @param activities   the list of activity data for a study
     * @param participants the participant data for a study
     * @param output       the output writer to use
     * @return number of participant records written
     * @throws IOException if error while writing
     */
    public int exportDataSetAsCsv(StudyDto studyDto, List<ActivityExtract> activities, List<Participant> participants,
                                  Writer output) throws IOException {
        ParticipantMetadataFormatter participantMetaFmt = new ParticipantMetadataFormatter();
        ActivityMetadataCollector activityMetadataCollector = new ActivityMetadataCollector();

        List<String> headers = new LinkedList<>();
        headers.addAll(participantMetaFmt.headers());

        Map<String, ActivityResponseCollector> responseCollectors = new HashMap<>();
        for (ActivityExtract activity : activities) {
            headers.addAll(activityMetadataCollector.headers(activity.getTag()));

            ActivityResponseCollector formatter = new ActivityResponseCollector(activity.getDefinition());
            headers.addAll(formatter.getHeaders());

            responseCollectors.put(activity.getTag(), formatter);
        }

        CSVWriter writer = new CSVWriter(output);
        writer.writeNext(headers.toArray(new String[] {}), false);

        int total = participants.size();
        int numWritten = 0;

        for (Participant pt : participants) {
            List<String> row = new LinkedList<>();
            try {
                row.addAll(participantMetaFmt.format(pt.getStatus(), pt.getUser()));
                ComponentDataSupplier supplier = new ComponentDataSupplier(pt.getUser().getAddress(), pt.getProviders());
                for (ActivityExtract activity : activities) {
                    ActivityResponseCollector formatter = responseCollectors.get(activity.getTag());
                    List<ActivityResponse> instances = pt.getResponses(activity.getTag());
                    if (instances.isEmpty()) {
                        row.addAll(activityMetadataCollector.emptyRow());
                        row.addAll(formatter.emptyRow());
                    } else {
                        if (instances.size() > 1) {
                            LOG.warn("[export] participant {} has {} instances of activity {} {}, will only export the latest one",
                                    pt.getUser().getGuid(), instances.size(),
                                    activity.getDefinition().getActivityCode(), activity.getDefinition().getVersionTag());
                        }
                        ActivityResponse instance = instances.stream()
                                .max(Comparator.comparing(ActivityResponse::getCreatedAt))
                                .get();
                        row.addAll(activityMetadataCollector.format(instance));
                        row.addAll(formatter.format(instance, supplier, ""));
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

            LOG.info("[export] ({}/{}) participant {} for study {}:"
                            + " status={}, hasProfile={}, hasAddress={}, numProviders={}, numInstances={}",
                    numWritten, total, pt.getUser().getGuid(), studyDto.getGuid(),
                    pt.getStatus().getEnrollmentStatus(), pt.getUser().hasProfile(), pt.getUser().hasAddress(),
                    pt.getProviders().size(), pt.getAllResponses().size());
        }

        writer.flush();
        return numWritten;
    }

    /**
     * Export the mapping data types consumable by Elasticsearch, by using predefined metadata and given activity definitions.
     *
     * @param activities list of activity data for a study
     * @return mapping of property names to type objects
     */
    public Map<String, Object> exportStudyDataMappings(List<ActivityExtract> activities) {
        Map<String, Object> mappings = new LinkedHashMap<>();

        ParticipantMetadataFormatter participantMetaFmt = new ParticipantMetadataFormatter();
        mappings.putAll(participantMetaFmt.mappings());

        ActivityMetadataCollector activityMetaColl = new ActivityMetadataCollector();

        for (ActivityExtract activity : activities) {
            mappings.putAll(activityMetaColl.mappings(activity.getTag()));

            ActivityResponseCollector activityRespColl = new ActivityResponseCollector(activity.getDefinition());
            mappings.putAll(activityRespColl.mappings());
        }

        return mappings;
    }
}
