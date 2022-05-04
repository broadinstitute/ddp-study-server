package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.CopyConfigurationSql;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.JdbiFormSection;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.FormSectionMembershipDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.User;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.event.ActivityInstanceCreationEventAction;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-off task to add adhoc symptom message to TestBoston in deployed environments.
 */
@Slf4j
public class OsteoAboutYouV2 implements CustomTask {

    private static final String DATA_FILE = "patches/about-you-v2.conf";
    private static final String UPDATES_DATA_FILE = "patches/about-you-updates.conf";
    private static final String TRANS_UPDATE = "trans-update";
    private static final String TRANS_UPDATE_OLD = "old_text";
    private static final String TRANS_UPDATE_NEW = "new_text";
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String ACTIVITY_CODE = "ABOUTYOU";
    private static final String ACTIVITY_CODE_2 = "ABOUTCHILD";
    private static final String ACTIVITY_CODE_RELEASE_SELF = "RELEASE_SELF";
    private static final String VERSION_TAG = "v2";

    private Config dataCfg;
    private Config updatesDataCfg;
    private Gson gson;
    private Config studyCfg;
    private Instant timestamp;
    private RevisionMetadata meta;
    private long studyId;

    private SqlHelper helper;
    private QuestionDao questionDao;
    private JdbiQuestion jdbiQuestion;
    private JdbiFormSection jdbiFormSection;
    private JdbiFormActivityFormSection jdbiFormActivityFormSection;
    private CopyConfigurationSql copyConfigurationSql;
    private ActivityI18nDao activityI18nDao;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }

        this.dataCfg = ConfigFactory.parseFile(file);

        File updatesFile = cfgPath.getParent().resolve(UPDATES_DATA_FILE).toFile();
        if (!updatesFile.exists()) {
            throw new DDPException("Data file is missing: " + updatesFile);
        }

        this.updatesDataCfg = ConfigFactory.parseFile(updatesFile);

        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }

        this.studyCfg = studyCfg;
        this.timestamp = Instant.now();
        this.gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        helper = handle.attach(SqlHelper.class);
        questionDao = handle.attach(QuestionDao.class);
        jdbiQuestion = handle.attach(JdbiQuestion.class);
        jdbiFormSection = handle.attach(JdbiFormSection.class);
        jdbiFormActivityFormSection = handle.attach(JdbiFormActivityFormSection.class);
        copyConfigurationSql = handle.attach(CopyConfigurationSql.class);
        activityI18nDao = handle.attach(ActivityI18nDao.class);
        ActivityDao activityDao = handle.attach(ActivityDao.class);
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        JdbiFormActivityFormSection jdbiFormActivityFormSection = handle.attach(JdbiFormActivityFormSection.class);
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
        UserDao userDao = handle.attach(UserDao.class);

        var studyDto = jdbiUmbrellaStudy.findByStudyGuid(studyCfg.getString("study.guid"));
        var adminUser = userDao.findUserByGuid(studyCfg.getString("adminUser.guid"))
                .orElseThrow(() -> new DDPException("Could not find admin user by guid"));

        studyId = studyDto.getId();
        var studyGuid = studyDto.getGuid();
        long activityId = ActivityBuilder.findActivityId(handle, studyId, ACTIVITY_CODE);
        long activityId2 = ActivityBuilder.findActivityId(handle, studyId, ACTIVITY_CODE_2);

        meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyGuid, ACTIVITY_CODE, VERSION_TAG));

        //change version
        var versionDto = activityDao.changeVersion(activityId, VERSION_TAG, meta);

        //update translatedName
        updateActivityName(activityId, ACTIVITY_CODE, dataCfg.getString("translatedName"));

        //add new section
        var section = jdbiFormActivityFormSection.findOrderedSectionMemberships(activityId, meta.getTimestamp()).get(0);
        long newFormSectionId = createSectionBefore(activityId, section);

        //add new WHO_IS_FILLING_ABOUTYOU question
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("who_filling_q")), FormBlockDef.class);

        sectionBlockDao.insertBlockForSection(
                activityId, newFormSectionId, QuestionDao.DISPLAY_ORDER_GAP, blockDef, versionDto.getRevId());

        log.info("Question ('WHO_IS_FILLING_ABOUTYOU') successfully added");

        // Delete copy configs
        List<String> questionsToDisable = new ArrayList<>(List.of(
                "HOW_HEAR",
                "EXPERIENCE",
                "RACE",
                "HISPANIC"));
        deleteCopyConfigs(questionsToDisable);

        // Disable questions
        long terminatedRevId = jdbiRevision.copyAndTerminate(section.getRevisionId(), meta);
        questionsToDisable.forEach(s -> disableQuestionDto(s, terminatedRevId));

        helper.updateActivityNameAndTitle(activityId, "About Your Osteosarcoma", "About Your Osteosarcoma");
        helper.updateActivityNameAndTitle(activityId2, "About Your child's Osteosarcoma", "About Your child's Osteosarcoma");
        updateTranslationSummaries(handle);

        // Edit editActivityCreationEvent
        editActivityCreationEvent(handle, adminUser.getId(), studyDto, activityId);
    }

    private long createSectionBefore(long activityId, FormSectionMembershipDto beforeSection) {
        final int sectionOrder = beforeSection.getDisplayOrder() - 1;
        final long newFormSectionId = jdbiFormSection.insert(jdbiFormSection.generateUniqueCode(), null);
        jdbiFormActivityFormSection.insert(activityId, newFormSectionId, beforeSection.getRevisionId(), sectionOrder);

        log.info("New section successfully created with displayOrder={} and revision={}",
                sectionOrder,
                beforeSection.getRevisionId());

        return newFormSectionId;
    }

    private void disableQuestionDto(String questionSid, long terminatedRevId) {

        QuestionDto questionDto = findQuestionBySid(questionSid);

        if (questionDto.getType().equals(QuestionType.TEXT)) {
            questionDao.disableTextQuestion(questionDto.getId(), meta);
        } else if (questionDto.getType().equals(QuestionType.PICKLIST)) {
            questionDao.disablePicklistQuestion(questionDto.getId(), meta);
        } else {
            throw new DDPException("There is no support to question type " + questionDto.getType());
        }

        final long blockId = helper.findQuestionBlockId(questionDto.getId());
        helper.updateFormSectionBlockRevision(blockId, terminatedRevId);

        log.info("Question ('{}') successfully disabled", questionSid);
    }

    private void editActivityCreationEvent(Handle handle, long adminUserId, StudyDto studyDto, long activityId) {

        long releaseSelfActivityId = ActivityBuilder.findActivityId(handle, studyId, ACTIVITY_CODE_RELEASE_SELF);

        EventConfiguration eventReleaseToAboutYou = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId())
                .stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS
                        && ((ActivityStatusChangeTrigger) event.getEventTrigger()).getStudyActivityId() == releaseSelfActivityId
                        && ((ActivityStatusChangeTrigger) event.getEventTrigger()).getInstanceStatusType() == InstanceStatusType.COMPLETE)
                .filter(event -> event.getEventActionType() == EventActionType.ACTIVITY_INSTANCE_CREATION
                        && ((ActivityInstanceCreationEventAction) event.getEventAction()).getStudyActivityId() == activityId)
                .filter(event -> StringUtils.isBlank(event.getCancelExpression()))
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find event RELEASE to ABOUTYOU"));

        helper.disableStudyEvent(eventReleaseToAboutYou.getEventConfigurationId());

        log.info("Inserting events configuration...");

        if (!dataCfg.hasPath("events")) {
            throw new DDPException("There is no 'events' configuration.");
        }
        List<? extends Config> events = dataCfg.getConfigList("events");
        if (events.size() != 1) {
            throw new DDPException("Expected " + 1 + " events but got " + events.size());
        }

        EventBuilder eventBuilder = new EventBuilder(studyCfg, studyDto, adminUserId);
        for (Config eventCfg : events) {
            eventBuilder.insertEvent(handle, eventCfg);
        }

        log.info("Events configuration has added in study {}", STUDY_GUID);
    }

    private QuestionDto findQuestionBySid(String questionSid) {
        return jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, questionSid)
                .orElseThrow(() -> new DDPException("Could not find question dto (" + questionSid + ")"));
    }

    private void deleteCopyConfigs(List<String> questionSids) {
        Set<Long> locationIds = helper.findCopyConfigsByQuestionIds(
                questionSids.stream().map(s -> findQuestionBySid(s).getId()).collect(Collectors.toSet()));

        Set<Long> configPairs = helper.findCopyConfigPairsByLocIds(locationIds);

        DBUtils.checkDelete(configPairs.size(), copyConfigurationSql.deleteCopyConfigPairs(configPairs));
        DBUtils.checkDelete(locationIds.size(), copyConfigurationSql.bulkDeleteCopyLocations(locationIds));
        log.info("Copy configs successfully deleted");
    }

    private void updateActivityName(long activityId, String activityCode, String name) {
        ActivityI18nDetail i18nDetail = activityI18nDao
                .findDetailsByActivityIdAndTimestamp(activityId, Instant.now().toEpochMilli())
                .iterator().next();
        var newI18nDetail = new ActivityI18nDetail(
                i18nDetail.getId(),
                i18nDetail.getActivityId(),
                i18nDetail.getLangCodeId(),
                i18nDetail.getIsoLangCode(),
                name,
                i18nDetail.getSecondName(),
                i18nDetail.getTitle(),
                i18nDetail.getSubtitle(),
                i18nDetail.getDescription(),
                i18nDetail.getRevisionId());
        activityI18nDao.updateDetails(List.of(newI18nDetail));
        log.info("Updated translatedName for activity {}", activityCode);
    }

    private void updateTranslationSummaries(Handle handle) {
        List<? extends Config> configList = updatesDataCfg.getConfigList(TRANS_UPDATE);
        for (Config config : configList) {
            updateSummary(config, handle);
        }
    }

    private void updateSummary(Config config, Handle handle) {
        String oldSum = String.format("%s%s%s", "%", config.getString(TRANS_UPDATE_OLD), "%");
        String newSum = config.getString(TRANS_UPDATE_NEW);

        handle.attach(SqlHelper.class).updateVarSubstitutionValue(oldSum, newSum);
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update i18n_template_substitution set substitution_value = :newValue where substitution_value like :oldValue")
        int _updateVarValueByOldValue(@Bind("oldValue") String oldValue, @Bind("newValue") String newValue);

        default void updateVarSubstitutionValue(String oldValue, String value) {
            int numUpdated = _updateVarValueByOldValue(oldValue, value);
            if (numUpdated < 1) {
                throw new DDPException("Expected to update a template variable value for value="
                        + oldValue + " but updated " + numUpdated);
            }
        }

        @SqlQuery("select block_id from block__question where question_id = :questionId")
        int findQuestionBlockId(@Bind("questionId") long questionId);

        @SqlQuery("select copy_location_id from copy_answer_location where question_stable_code_id in (<questionSid>)")
        Set<Long> findCopyConfigsByQuestionIds(
                @BindList(value = "questionSid", onEmpty = BindList.EmptyHandling.NULL) Set<Long> questionIds);

        @SqlQuery("select copy_configuration_pair_id from copy_configuration_pair "
                + "where source_location_id in (<locIds>) or target_location_id in (<locIds>)")
        Set<Long> findCopyConfigPairsByLocIds(
                @BindList(value = "locIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> locIds);

        @SqlUpdate("update form_section__block set revision_id = :revisionId where block_id = :blockId")
        void updateFormSectionBlockRevision(@Bind("blockId") long blockId, @Bind("revisionId") long revisionId);

        @SqlUpdate("update i18n_activity_detail set name = :name, title = :title where study_activity_id = :studyActivityId")
        int _updateActivityNameAndTitle(@Bind("studyActivityId") long studyActivityId,
                                @Bind("name") String name,
                                @Bind("title") String title);

        default void updateActivityNameAndTitle(long studyActivityId, String name, String title) {
            int numUpdated = _updateActivityNameAndTitle(studyActivityId, name, title);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 row for studyActivityId="
                        + studyActivityId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update event_configuration set is_active = false where event_configuration_id = :eventId")
        int _disableStudyEvent(@BindList("eventId") long eventId);

        default void disableStudyEvent(long eventId) {
            int numUpdated = _disableStudyEvent(eventId);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 row for event_configuration_id ="
                        + eventId + " but updated " + numUpdated);
            }
        }
    }
}
