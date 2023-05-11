package org.broadinstitute.ddp.export;

import static org.broadinstitute.ddp.export.ExportUtil.extractParticipantsFromResultSet;
import static org.broadinstitute.ddp.export.ExportUtil.getSnapshottedMailAddress;
import static org.broadinstitute.ddp.export.ExportUtil.hideProtectedValue;
import static org.broadinstitute.ddp.model.activity.types.ComponentType.MAILING_ADDRESS;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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
import com.opencsv.CSVWriter;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.content.I18nTemplateRenderFacade;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.StudyDataAliasDao;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
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
import org.broadinstitute.ddp.db.dto.EquationQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.equation.QuestionEvaluator;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.export.collectors.ActivityAttributesCollector;
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
import org.broadinstitute.ddp.export.json.structured.EquationQuestionRecord;
import org.broadinstitute.ddp.export.json.structured.FileRecord;
import org.broadinstitute.ddp.export.json.structured.ParticipantProfile;
import org.broadinstitute.ddp.export.json.structured.ParticipantRecord;
import org.broadinstitute.ddp.export.json.structured.PdfConfigRecord;
import org.broadinstitute.ddp.export.json.structured.PicklistQuestionRecord;
import org.broadinstitute.ddp.export.json.structured.MatrixQuestionRecord;
import org.broadinstitute.ddp.export.json.structured.QuestionRecord;
import org.broadinstitute.ddp.export.json.structured.SimpleQuestionRecord;
import org.broadinstitute.ddp.export.json.structured.UserRecord;
import org.broadinstitute.ddp.json.EquationResponse;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.TabularBlockDef;
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
import org.broadinstitute.ddp.model.activity.instance.answer.DecimalAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.FileInfo;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.MatrixAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedMatrixCell;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.es.StudyDataAlias;
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
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.service.ConsentService;
import org.broadinstitute.ddp.service.FileUploadService;
import org.broadinstitute.ddp.service.MedicalRecordService;
import org.broadinstitute.ddp.service.OLCService;
import org.broadinstitute.ddp.service.PdfService;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.jdbi.v3.core.Handle;

@Slf4j
public class DataExporter {
    public static final String TIMESTAMP_PATTERN = "MM/dd/yyyy HH:mm:ss";
    public static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter
            .ofPattern(TIMESTAMP_PATTERN).withZone(ZoneOffset.UTC);

    private static final String REQUEST_TYPE = "_doc";

    // A cache for user auth0 emails, storing (auth0UserId -> email).
    private static final Map<String, String> emailStore = new HashMap<>();

    private final Config cfg;
    private final Gson gson;
    private final Set<String> componentNames;
    private final PdfService pdfService;
    private final FileUploadService fileService;
    private final RestHighLevelClient esClient;
    private final AddressService addressService;

    public static String makeExportCSVFilename(String studyGuid, Instant timestamp) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").withZone(ZoneOffset.UTC);
        return String.format("%s_%s.csv", studyGuid, fmt.format(timestamp));
    }

    public static void evictCachedAuth0Emails(Set<String> auth0UserIds) {
        if (CollectionUtils.isNotEmpty(auth0UserIds)) {
            auth0UserIds.forEach(emailStore::remove);
        }
    }

    public DataExporter(Config cfg) {
        this.cfg = cfg;
        this.gson = GsonUtil.standardGson();
        this.pdfService = new PdfService();
        this.fileService = FileUploadService.fromConfig(cfg);
        this.addressService = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY),
                cfg.getString(ConfigFile.GEOCODING_API_KEY));
        try {
            this.esClient = ElasticsearchServiceUtil.getElasticsearchClient(cfg);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
        componentNames = new HashSet<>();
        componentNames.add("MAILING_ADDRESS");
        componentNames.add("INITIAL_BIOPSY");
        componentNames.add("INSTITUTION");
        componentNames.add("PHYSICIAN");
    }

    public static void clearCachedAuth0Emails() {
        ExportUtil.clearCachedAuth0Emails(emailStore);
    }

    public static void fetchAndCacheAuth0Emails(Handle handle, StudyDto studyDto, Set<String> userIds) {
        ExportUtil.fetchAndCacheAuth0Emails(handle, studyDto, userIds, emailStore);
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

        log.info("[export] found {} activities for study {}", activities.size(), studyGuid);

        return activities;
    }

    /**
     * Find and set the max number of instances seen per participant across the study for each given activity extract.
     * Activities that are defined with only one instance per participant will not be computed so the counts might not
     * be totally accurate. Otherwise, this will find the current number of instances for each activity.
     *
     * @param handle     the database handle
     * @param activities the list of activities to look at
     */
    public static void computeMaxInstancesSeen(Handle handle, List<ActivityExtract> activities) {
        var instanceDao = handle.attach(ActivityInstanceDao.class);
        for (ActivityExtract activity : activities) {
            ExportUtil.computeMaxInstancesSeen(instanceDao, activity);
        }
    }

    /**
     * Find and set the attribute names seen across all participants in study for each given activity extract.
     *
     * @param handle     the database handle
     * @param activities the list of activities to look at
     */
    public static void computeActivityAttributesSeen(Handle handle, List<ActivityExtract> activities) {
        var instanceDao = handle.attach(ActivityInstanceDao.class);
        for (ActivityExtract activity : activities) {
            ExportUtil.computeActivityAttributesSeen(instanceDao, activity);
        }
    }

    public List<Participant> extractParticipantDataSet(Handle handle, StudyDto studyDto) {
        return extractParticipantDataSetByIds(handle, studyDto, null);
    }

    private List<Participant> extractParticipantDataSetByGuids(Handle handle, StudyDto studyDto, Set<String> userGuids) {
        Stream<Participant> resultset = null;
        try {
            if (userGuids == null) {
                resultset = handle.attach(ParticipantDao.class).findParticipantsWithFullData(studyDto.getId());
            } else {
                resultset = handle.attach(ParticipantDao.class).findParticipantsWithFullDataByUserGuids(studyDto.getId(), userGuids);
            }
            return extractParticipantsFromResultSet(handle, studyDto, resultset, emailStore);
        } finally {
            if (resultset != null) {
                resultset.close();
            }
        }
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
        List<StudyDataAlias> studyDataAliases = handle.attach(StudyDataAliasDao.class).findAliasesByStudy(studyDto.getGuid());
        hideProtectedAnswerValues(participants, studyDataAliases);
        exportToElasticsearch(handle, studyDto, activityExtracts, participants, exportStructuredDocument);
    }

    private void hideProtectedAnswerValues(List<Participant> participants, List<StudyDataAlias> studyDataAliases) {
        for (Participant participant : participants) {
            for (StudyDataAlias hiddenAlias : studyDataAliases) {
                hideProtectedValue(participant, hiddenAlias);
            }
        }
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

        String index = ElasticsearchServiceUtil.getIndexForStudy(
                handle,
                studyDto,
                exportStructuredDocument ? ElasticSearchIndexType.PARTICIPANTS_STRUCTURED : ElasticSearchIndexType.PARTICIPANTS
        );

        List<Participant> batch = new ArrayList<>();
        Iterator<Participant> iter = participants.iterator();
        int exportsSoFar = 0;
        while (iter.hasNext()) {
            Participant participant = iter.next();
            try {
                batch.add(participant);
                if (batch.size() == maxExtractSize || !iter.hasNext()) {
                    int extractSize = batch.size();
                    log.info("[export] exporting {} participant records to index {}", extractSize, index);

                    convertInfoToJSONAndExportToES(
                            handle,
                            activities,
                            batch,
                            studyDto,
                            index,
                            exportStructuredDocument
                    );
                    exportsSoFar += extractSize;
                    batch.clear();

                    log.info("[export] have now exported {} participants out of {} for study {}",
                            exportsSoFar, participants.size(), studyDto.getGuid());
                }
            } catch (Exception e) {
                log.error("[export] failed to export participants for study {}, continuing... ", studyDto.getGuid(), e);
            }
        }
    }

    public List<ActivityExtract> exportActivityDefinitionsToElasticsearch(Handle handle, StudyDto studyDto) {

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
            activityDefinitions.put("activityName", I18nTemplateRenderFacade.INSTANCE.renderTemplateWithDefaultValues(
                    activityName, null, "en"));
            activityDefinitions.put("activityVersion", activity.getDefinition().getVersionTag());
            activityDefinitions.put("parentActivityCode", activity.getDefinition().getParentActivityCode());
            activityDefinitions.put("displayOrder", activity.getDefinition().getDisplayOrder());
            activityDefinitions.put("showActivityStatus", activity.getDefinition().showActivityStatus());
            activityDefinitions.putAll(formatter.questionDefinitions());

            allActivityDefs.put(activity.getTag(), activityDefinitions);
        }

        String index = ElasticsearchServiceUtil.getIndexForStudy(
                handle,
                studyDto,
                ElasticSearchIndexType.ACTIVITY_DEFINITION
        );

        try {
            exportDataToElasticSearch(index, allActivityDefs);
        } catch (IOException e) {
            log.error("[export] failed during export to index {}", index, e);
        }

        return activityExtracts;
    }

    private void exportDataToElasticSearch(String index, Map<String, Object> data) throws IOException {
        if (data.isEmpty()) {
            return;
        }

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

        BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);

        if (bulkResponse.hasFailures()) {
            log.error(bulkResponse.buildFailureMessage());
        }
        log.info("Exported data to ES");
    }

    public void exportUsersToElasticsearch(Handle handle, StudyDto studyDto, Set<Long> userIds) {

        //load participants
        Stream<Participant> resultset = null;
        List<Participant> participants;
        try {
            if (userIds != null) {
                resultset = handle.attach(ParticipantDao.class).findParticipantsWithUserProfileByStudyIdAndUserIds(
                        studyDto.getId(), userIds);
            } else {
                resultset = handle.attach(ParticipantDao.class)
                        .findParticipantsWithUserProfileByStudyId(studyDto.getId());
            }
            participants = extractParticipantsFromResultSet(handle, studyDto, resultset, emailStore);
        } finally {
            if (resultset != null) {
                resultset.close();
            }
        }


        //load governances and build data structures to get proxies and governedUsers
        Map<String, Set<String>> proxiesMap = new HashMap<>();
        Map<String, Set<String>> governedUsersMap = new HashMap<>();
        Set<String> operatorGuids = new HashSet<>();
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        try (Stream<Governance> allGovernances = userGovernanceDao.findActiveGovernancesByStudyGuid(studyDto.getGuid())) {
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
        }

        Map<String, Object> allUsers = new HashMap<>();
        //load participants
        participants.forEach(participant -> {
            User user = participant.getUser();
            allUsers.put(user.getGuid(), createUserRecord(user, proxiesMap, governedUsersMap));
        });

        //load operators
        try (Stream<User> users = handle.attach(UserDao.class).findUsersAndProfilesByGuids(operatorGuids)) {
            List<User> operators = extractUsersFromResultSet(handle, studyDto, users);
            operators.forEach(user -> {
                if (allUsers.containsKey(user.getGuid())) {
                    //operator/user is also a participant... skip
                    return;
                }
                allUsers.put(user.getGuid(), createUserRecord(user, proxiesMap, governedUsersMap));
            });
        }

        String index = ElasticsearchServiceUtil.getIndexForStudy(
                handle,
                studyDto,
                ElasticSearchIndexType.USERS
        );
        log.info("[export] exporting {} user records to index {}", allUsers.size(), index);

        try {
            exportDataToElasticSearch(index, allUsers);
        } catch (IOException e) {
            log.error("[export] failed during export to index {}", index, e);
        }
        log.info("[export] completed exporting {} user records to index {}", allUsers.size(), index);

    }

    private UserRecord createUserRecord(User user,
                                        Map<String, Set<String>> proxiesMap,
                                        Map<String, Set<String>> governedUsersMap) {
        UserProfile userProfile = user.getProfile();
        if (userProfile == null) {
            userProfile = UserProfile.builder().userId(user.getId()).build();
        }
        ParticipantProfile profile = new ParticipantProfile(userProfile.getFirstName(), userProfile.getLastName(),
                user.getGuid(), user.getHruid(), user.getLegacyAltPid(), user.getLegacyShortId(), user.getEmail().orElse(null),
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
                    if (user.hasAuth0Account() == false) {
                        return;
                    }
                    
                    var auth0UserId = user.getAuth0UserId().get();

                    String email = emailStore.get(auth0UserId);
                    if (email == null) {
                        usersMissingEmails.put(auth0UserId, user.getGuid());
                    } else {
                        user.setEmail(email);
                    }
                })
                .collect(Collectors.toMap(User::getGuid, user -> user));

        if (!usersMissingEmails.isEmpty()) {
            ExportUtil.fetchAndCacheAuth0Emails(handle, studyDto, usersMissingEmails.keySet(), emailStore)
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
                                                List<Participant> participants,
                                                StudyDto studyDto,
                                                String index,
                                                boolean exportStructuredDocument
    ) throws IOException {
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
        Map<String, List<Governance>> userProxies;
        try (Stream<Governance> governanceStream =
                     handle.attach(UserGovernanceDao.class).findActiveGovernancesByStudyGuid(studyDto.getGuid())) {
            userProxies = governanceStream.collect(Collectors.groupingBy(Governance::getGovernedUserGuid));
        }

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

        enrichWithDSMEventDates(handle, medicalRecordService, governancePolicy, studyDto.getId(), participants, participantProxyGuids);
        enrichWithFileRecords(handle, fileService, studyDto.getId(), participants);

        StudyExtract studyExtract = new StudyExtract(activities,
                studyPdfConfigs,
                configPdfVersions,
                participantProxyGuids);

        Map<String, String> participantRecords = prepareParticipantRecordsForJSONExport(
                studyExtract, participants, exportStructuredDocument, handle, medicalRecordService);

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

        BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);

        if (bulkResponse.hasFailures()) {
            log.error(bulkResponse.buildFailureMessage());
        }
    }

    void enrichWithDSMEventDates(Handle handle,
                                 MedicalRecordService medicalRecordService,
                                 GovernancePolicy governancePolicy,
                                 long studyId,
                                 List<Participant> dataset,
                                 Map<String, List<String>> participantProxyGuids) {

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
            String participantGuid = participant.getUser().getGuid();
            String operatorGuid = (participantProxyGuids.containsKey(participantGuid))
                    ? participantProxyGuids.get(participantGuid).stream().findFirst().get()
                    : participantGuid;
            if (governancePolicy != null) {
                AgeOfMajorityRule aomRule = null;
                try {
                    aomRule = governancePolicy.getApplicableAgeOfMajorityRule(handle,
                            pexInterpreter,
                            participantGuid,
                            operatorGuid)
                            .orElse(null);
                } catch (Exception e) {
                    log.error("Error while evaluating age-of-majority rules for participant {} operator {} and studyId {}, ignoring.",
                            participantGuid, operatorGuid, studyId, e); //log & skip AOM content for the ptp.
                }

                if (birthDate != null && aomRule != null) {
                    dateOfMajority = aomRule.getDateOfMajority(birthDate);
                }
            }

            participant.setBirthDate(birthDate);
            participant.setDateOfDiagnosis(diagnosisDate);
            participant.setDateOfMajority(dateOfMajority);
        });
    }

    private void enrichWithFileRecords(Handle handle, FileUploadService fileService, long studyId, List<Participant> participants) {
        var uploadDao = handle.attach(FileUploadDao.class);
        Map<Long, List<FileRecord>> participantIdToFiles = new HashMap<>();
        Set<Long> participantIds = participants.stream().map(p -> p.getUser().getId()).collect(Collectors.toSet());
        try (var stream = uploadDao.findVerifiedAndAssociatedUploadsForParticipants(studyId, participantIds)) {
            stream.forEach(upload -> {
                long key = upload.getParticipantUserId();
                String bucket = fileService.getBucketForUpload(upload);
                FileRecord record = new FileRecord(bucket, upload);
                participantIdToFiles.computeIfAbsent(key, id -> new ArrayList<>()).add(record);
            });
        }
        for (var participant : participants) {
            long key = participant.getUser().getId();
            participant.addAllFiles(participantIdToFiles.get(key));
        }
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
                String elasticSearchDocument;
                if (exportStructuredDocument) {
                    elasticSearchDocument = formatParticipantToStructuredJSON(studyExtract,
                            extract,
                            handle,
                            medicalRecordService);
                } else {
                    elasticSearchDocument = formatParticipantToFlatJSON(handle, studyExtract.getActivities(), extract);
                }
                participantsRecords.put(extract.getUser().getGuid(), elasticSearchDocument);
            } catch (Exception e) {
                String participantGuid = extract.getStatus().getUserGuid();
                String studyGuid = extract.getStatus().getStudyGuid();
                log.error("Error while formatting data into {} json for participant {} and study {}, skipping",
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
     * @return a json streen representing the participant's data
     */
    private String formatParticipantToFlatJSON(Handle handle, List<ActivityExtract> activities,
                                               Participant extract) {
        ParticipantMetadataFormatter participantMetaFmt = new ParticipantMetadataFormatter();
        ActivityMetadataCollector activityMetadataCollector = new ActivityMetadataCollector();
        Map<String, ActivityResponseCollector> responseCollectors = new HashMap<>();
        for (ActivityExtract activity : activities) {
            ActivityResponseCollector formatter = new ActivityResponseCollector(activity.getDefinition());
            responseCollectors.put(activity.getTag(), formatter);
        }

        Map<String, String> recordForParticipant = new LinkedHashMap<>(participantMetaFmt.records(extract.getStatus(), extract.getUser()));

        ComponentDataSupplier supplier = new ComponentDataSupplier(
                extract.getUser().getAddress(),
                extract.getNonDefaultMailAddresses(),
                extract.getProviders());
        for (ActivityExtract activity : activities) {
            ActivityResponseCollector activityResponseCollector = responseCollectors.get(activity.getTag());
            List<ActivityResponse> instances = extract.getResponses(activity.getTag());
            if (!instances.isEmpty()) {
                if (instances.size() > 1) {
                    log.warn("[export] participant {} has {} instances of activity {} {}, will only export the latest one",
                            extract.getUser().getGuid(), instances.size(),
                            activity.getDefinition().getActivityCode(), activity.getDefinition().getVersionTag());
                }
                ActivityResponse instance = instances.stream()
                        .max(Comparator.comparing(ActivityResponse::getCreatedAt))
                        .get();
                recordForParticipant.putAll(activityMetadataCollector.records(activity.getTag(), instance));
                recordForParticipant.putAll(activityResponseCollector.records(instance, supplier, null));
            } else {
                boolean hasParent = StringUtils.isNotBlank(activity.getDefinition().getParentActivityCode());
                recordForParticipant.putAll(activityMetadataCollector.emptyRecord(activity.getTag(), hasParent));
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
        User participantUser = participant.getUser();

        // Profile
        ParticipantProfile.Builder builder = ParticipantProfile.builder();
        UserProfile userProfile = participantUser.getProfile();
        if (userProfile != null) {
            builder.setFirstName(userProfile.getFirstName());
            builder.setLastName(userProfile.getLastName());
            builder.setPreferredLanguage(userProfile.getPreferredLangCode());
            builder.setDoNotContact(userProfile.getDoNotContact());
        }
        builder.setGuid(participantUser.getGuid())
                .setHruid(participantUser.getHruid())
                .setLegacyAltPid(participantUser.getLegacyAltPid())
                .setLegacyShortId(participantUser.getLegacyShortId())
                .setEmail(participantUser.getEmail().orElse(null))
                .setCreatedAt(participantUser.getCreatedAt());
        ParticipantProfile participantProfile = builder.build();

        // ActivityInstances (aka "surveys")
        List<ActivityInstanceRecord> activityInstanceRecords = new ArrayList<>();
        Map<String, Set<String>> userActivityVersions = new HashMap<>();
        for (ActivityExtract activityExtract : studyExtract.getActivities()) {
            List<ActivityResponse> instances = participant.getResponses(activityExtract.getTag()).stream()
                    .sorted(Comparator.comparing(ActivityResponse::getCreatedAt).reversed())
                    .collect(Collectors.toList());
            for (ActivityResponse instance : instances) {
                ActivityInstanceStatusDto lastStatus = instance.getLatestStatus();
                List<QuestionRecord> questionsAnswers = createQuestionRecordsForActivity(activityExtract.getDefinition(),
                        instance, participant, handle);
                ActivityInstanceRecord activityInstanceRecord = new ActivityInstanceRecord(
                        instance.getActivityVersionTag(),
                        instance.getActivityCode(),
                        instance.getGuid(),
                        instance.getParentInstanceGuid(),
                        lastStatus.getType(),
                        instance.getCreatedAt(),
                        instance.getFirstCompletedAt(),
                        lastStatus.getUpdatedAt(),
                        questionsAnswers
                );

                // We're only exposing a few substitutions as "attributes" on the activity instances.
                Map<String, String> subs = participant.getActivityInstanceSubstitutions(instance.getId());
                for (String name : ActivityAttributesCollector.EXPOSED_ATTRIBUTES) {
                    if (subs.containsKey(name)) {
                        activityInstanceRecord.putAttribute(name, subs.get(name));
                    }
                }

                activityInstanceRecords.add(activityInstanceRecord);
                if (lastStatus.getType().equals(InstanceStatusType.COMPLETE)) {
                    userActivityVersions
                            .computeIfAbsent(instance.getActivityCode(), key -> new HashSet<>())
                            .add(instance.getActivityVersionTag());
                }
            }
        }

        String userGuid = participantUser.getGuid();
        String studyGuid = statusDto.getStudyGuid();

        List<PdfConfigInfo> pdfConfigInfoList = findPdfConfigsForStudyUser(
                studyExtract.getStudyPdfConfigs(), studyExtract.getPdfVersions(), userActivityVersions);
        List<PdfConfigRecord> pdfConfigRecords = new ArrayList<>();
        for (PdfConfigInfo info : pdfConfigInfoList) {
            pdfConfigRecords.add(new PdfConfigRecord(info.getConfigName(), info.getDisplayName()));
        }

        // Retrieving information to compute the dsm record
        MedicalRecordService.ParticipantConsents consents = medicalRecordService
                .fetchBloodAndTissueConsents(handle, participantUser.getId(), userGuid, null, statusDto.getStudyId(), studyGuid);

        DsmComputedRecord dsmComputedRecord =
                new DsmComputedRecord(participant.getBirthDate(),
                        participant.getDateOfMajority(),
                        participant.getDateOfDiagnosis(),
                        consents.hasConsentedToBloodDraw(),
                        consents.hasConsentedToTissueSample(),
                        pdfConfigRecords);

        List<String> proxies = studyExtract.getParticipantProxyGuids().get(participantUser.getGuid());
        if (proxies == null) {
            proxies = List.of();
        }

        ParticipantRecord participantRecord = new ParticipantRecord(
                statusDto.getEnrollmentStatus(),
                statusDto.getValidFromMillis(),
                participantProfile,
                activityInstanceRecords,
                participant.getProviders(),
                participantUser.getAddress(),
                dsmComputedRecord,
                proxies,
                participant.getInvitations(),
                participant.getFiles()
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
                                                                  Participant participant, Handle handle) {
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
                } else if (formBlock.getBlockType() == BlockType.TABULAR) {
                    TabularBlockDef tabularBlock = (TabularBlockDef) formBlock;
                    allQuestions.addAll(tabularBlock.getQuestions().collect(Collectors.toList()));
                } else if (formBlock.getBlockType() == BlockType.QUESTION) {
                    QuestionBlockDef questionBlock = (QuestionBlockDef) formBlock;
                    allQuestions.add(questionBlock.getQuestion());
                } else if (formBlock.getBlockType() == BlockType.COMPONENT) {
                    ComponentBlockDef componentBlockDef = (ComponentBlockDef) formBlock;
                    QuestionDef componentDef;
                    if (componentBlockDef.getComponentType() == MAILING_ADDRESS) {
                        componentDef = new CompositeQuestionDef(componentBlockDef.getComponentType().name(), false,
                                dummyTemplate, null, null, dummyValidations, null,
                                null, false, true, null, null, false, false, null);
                    } else {
                        componentDef = new CompositeQuestionDef(((PhysicianInstitutionComponentDef) componentBlockDef).getInstitutionType()
                                .name(), false, dummyTemplate, null, null, dummyValidations, null,
                                null, false, true, null, null, false, false, null);
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
                    questionRecords.add(createRecordForComponent(instance, composite.getStableId(), participant));
                    continue;
                }
                if (composite.shouldUnwrapChildQuestions() && instance.hasAnswer(composite.getStableId())) {
                    // There should be one row with one answer per child. Put them into the response object so we can recurse.
                    ((CompositeAnswer) instance.getAnswer(composite.getStableId())).getValue().stream()
                            .flatMap(row -> row.getValues().stream())
                            .forEach(instance::putAnswer);
                    composite.getChildren().forEach(child -> questionRecords.add(createRecordForQuestion(child, instance, handle)));
                    continue;
                }
            }
            questionRecords.add(createRecordForQuestion(question, instance, handle));
        }

        return questionRecords.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private QuestionRecord createRecordForComponent(FormResponse formResponse, String component, Participant participant) {
        ComponentQuestionRecord record = null;
        List<List<String>> values;
        switch (component) {
            case "MAILING_ADDRESS":
                values = new MailingAddressFormatter().collectAsAnswer(
                        getSnapshottedMailAddress(
                                participant.getNonDefaultMailAddresses(), formResponse.getId(), participant.getUser().getAddress()));
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
    private QuestionRecord createRecordForQuestion(QuestionDef question, FormResponse instance, Handle handle) {
        if (question.isDeprecated() && !instance.hasAnswer(question.getStableId())) {
            return null;
        }
        Answer answer = instance.getAnswer(question.getStableId());
        if (answer == null && question.getQuestionType() != QuestionType.EQUATION) {
            return null;
        }
        QuestionType type = question.getQuestionType();
        if (type == QuestionType.DATE) {
            DateValue value = (DateValue) answer.getValue();
            return new DateQuestionRecord(question.getStableId(), value);
        } else if (type == QuestionType.FILE) {
            List<FileInfo> fileInfos = ((FileAnswer) answer).getValue();
            List<Long> uploadIds = fileInfos == null
                    ? Collections.emptyList() :
                    fileInfos.stream().map(FileInfo::getUploadId).collect(Collectors.toList());
            return new SimpleQuestionRecord(type, question.getStableId(), uploadIds);
        } else if (type == QuestionType.PICKLIST) {
            List<SelectedPicklistOption> selected = ((PicklistAnswer) answer).getValue();
            return new PicklistQuestionRecord(question.getStableId(), selected);
        } else if (type == QuestionType.MATRIX) {
            List<SelectedMatrixCell> selected = ((MatrixAnswer) answer).getValue();
            return new MatrixQuestionRecord(question.getStableId(), selected);
        } else if (type == QuestionType.COMPOSITE) {
            List<AnswerRow> rows = ((CompositeAnswer) answer).getValue();
            return new CompositeQuestionRecord(question.getStableId(), rows);
        } else if (type == QuestionType.DECIMAL) {
            return new SimpleQuestionRecord(type, question.getStableId(), ((DecimalAnswer) answer).getValueAsBigDecimal());
        } else if (type == QuestionType.EQUATION) {
            final Optional<QuestionDto> equationDto = handle.attach(QuestionDao.class).getJdbiQuestion()
                    .findDtoByStableIdAndInstanceGuid(question.getStableId(), instance.getGuid());
            final var questionEvaluator = new QuestionEvaluator(handle, instance.getGuid());
            final EquationResponse response = questionEvaluator.evaluate((EquationQuestionDto) equationDto.get());
            return new EquationQuestionRecord(question.getStableId(), response);
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
    private int exportCsvToOutput(Handle handle, StudyDto studyDto, Writer output) throws IOException {
        List<ActivityExtract> activities = extractActivities(handle, studyDto);
        List<Participant> dataset = extractParticipantDataSet(handle, studyDto);
        computeMaxInstancesSeen(handle, activities);
        computeActivityAttributesSeen(handle, activities);
        return exportDataSetAsCsv(studyDto, activities, dataset.iterator(), output);
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
        ParticipantMetadataFormatter participantMetaFmt = new ParticipantMetadataFormatter();
        ActivityMetadataCollector activityMetadataCollector = new ActivityMetadataCollector();

        List<String> headers = new LinkedList<>(participantMetaFmt.headers());

        Map<String, Integer> activityTagToNormalizedMaxInstanceCounts = new HashMap<>();
        Map<String, ActivityResponseCollector> responseCollectors = new HashMap<>();
        Map<String, ActivityAttributesCollector> attributesCollectors = new HashMap<>();
        for (ActivityExtract activity : activities) {
            Integer maxInstances = activity.getMaxInstancesSeen();
            if (maxInstances == null || maxInstances < 1) {
                log.warn("Found max instances count {} for activity tag {}, defaulting to 1", maxInstances, activity.getTag());
                // NOTE: default to one so we always have one set of columns even if no participant has an instance.
                maxInstances = 1;
            }

            ActivityResponseCollector responseCollector = new ActivityResponseCollector(activity.getDefinition());
            ActivityAttributesCollector attributesCollector = new ActivityAttributesCollector(activity.getAttributesSeen());

            activityTagToNormalizedMaxInstanceCounts.put(activity.getTag(), maxInstances);
            responseCollectors.put(activity.getTag(), responseCollector);
            attributesCollectors.put(activity.getTag(), attributesCollector);

            boolean hasParent = StringUtils.isNotBlank(activity.getDefinition().getParentActivityCode());
            for (var i = 1; i <= maxInstances; i++) {
                List<String> activityMetadataColumns;
                if (i == 1) {
                    activityMetadataColumns = activityMetadataCollector.headers(activity.getTag(), hasParent);
                } else {
                    activityMetadataColumns = activityMetadataCollector.headers(activity.getTag(), hasParent, i);
                }
                headers.addAll(activityMetadataColumns);
                headers.addAll(attributesCollector.headers());
                headers.addAll(responseCollector.getHeaders());
            }
        }

        CSVWriter writer = new CSVWriter(output);
        writer.writeNext(headers.toArray(new String[]{}), false);

        int numWritten = 0;
        while (participants.hasNext()) {
            Participant pt = participants.next();
            List<String> row;
            try {
                row = new LinkedList<>(participantMetaFmt.format(pt.getStatus(), pt.getUser()));
                ComponentDataSupplier supplier = new ComponentDataSupplier(
                        pt.getUser().getAddress(),
                        pt.getNonDefaultMailAddresses(),
                        pt.getProviders());
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
                        row.addAll(activityMetadataCollector.format(instance));
                        row.addAll(attributesCollector.format(subs));
                        row.addAll(responseCollector.format(instance, supplier, ""));
                        numInstancesProcessed++;
                    }

                    boolean hasParent = StringUtils.isNotBlank(activity.getDefinition().getParentActivityCode());
                    while (numInstancesProcessed < maxInstances) {
                        row.addAll(activityMetadataCollector.emptyRow(hasParent));
                        row.addAll(attributesCollector.emptyRow());
                        row.addAll(responseCollector.emptyRow());
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

    /**
     * Export the mapping data types consumable by Elasticsearch, by using predefined metadata and given activity definitions.
     *
     * @param activities list of activity data for a study
     * @return mapping of property names to type objects
     */
    public Map<String, Object> exportStudyDataMappings(List<ActivityExtract> activities) {

        ParticipantMetadataFormatter participantMetaFmt = new ParticipantMetadataFormatter();
        Map<String, Object> mappings = new LinkedHashMap<>(participantMetaFmt.mappings());

        ActivityMetadataCollector activityMetaColl = new ActivityMetadataCollector();

        for (ActivityExtract activity : activities) {
            boolean hasParent = StringUtils.isNotBlank(activity.getDefinition().getParentActivityCode());
            mappings.putAll(activityMetaColl.mappings(activity.getTag(), hasParent));

            ActivityResponseCollector activityRespColl = new ActivityResponseCollector(activity.getDefinition());
            mappings.putAll(activityRespColl.mappings());
        }

        return mappings;
    }

    static List<Participant> extractParticipantDataSetByIds(Handle handle, StudyDto studyDto, Set<Long> batch) {
        return ExportUtil.extractParticipantDataSetByIds(handle, studyDto, batch, emailStore);
    }
}
