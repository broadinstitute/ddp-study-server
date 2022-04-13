package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.CopyConfigurationSql;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiFormSection;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One-off task to add adhoc symptom message to TestBoston in deployed environments.
 */
@Slf4j
public class OsteoAboutChildV2 implements CustomTask {
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String UPDATES_DATA_FILE = "patches/about-you-child-updates.conf";
    private static final String TRANS_UPDATE = "trans-update";
    private static final String TRANS_INSERT = "summary-trans-insert";
    private static final String TRANS_UPDATE_OLD = "old_text";
    private static final String TRANS_UPDATE_NEW = "new_text";
    private static final String ACTIVITY_CODE = "ABOUTCHILD";
    private static final String VERSION_TAG = "v2";

    private static final String SUMMARY_INSERT_ACTIVITY_CODE = "activity_code";
    private static final String SUMMARY_INSERT_STATUS_TYPE_CODE = "status_type_code";
    private static final String SUMMARY_INSERT_LANGUAGE_CODE = "language_code";
    private static final String SUMMARY_INSERT_TEXT = "text";

    private Config studyCfg;
    private Instant timestamp;
    private Config updatesDataCfg;

    private JdbiActivityVersion jdbiVersion;


    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
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
    }

    @Override
    public void run(Handle handle) {

        final User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid"))
                .orElseThrow(() -> new DDPException("Could not find admin user by guid"));

        final StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class)
                .findByStudyGuid(studyCfg.getString("study.guid"));

        jdbiVersion = handle.attach(JdbiActivityVersion.class);

        final SqlHelper helper = handle.attach(SqlHelper.class);
        final ActivityDao activityDao = handle.attach(ActivityDao.class);
        final QuestionDao questionDao = handle.attach(QuestionDao.class);
        final JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        final JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        final JdbiFormSection jdbiFormSection = handle.attach(JdbiFormSection.class);
        final JdbiFormActivityFormSection jdbiFormActivityFormSection = handle.attach(JdbiFormActivityFormSection.class);
        final CopyConfigurationSql copyConfigurationSql = handle.attach(CopyConfigurationSql.class);

        final String studyGuid = studyDto.getGuid();
        final long studyId = studyDto.getId();
        final long activityId = ActivityBuilder.findActivityId(handle, studyId, ACTIVITY_CODE);

        final String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyGuid, ACTIVITY_CODE, VERSION_TAG);

        final RevisionMetadata meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), reason);

        //change version
        ActivityVersionDto activityVersion = activityDao.changeVersion(activityId, VERSION_TAG, meta);

        //add new section
        final var firstSection =
                jdbiFormActivityFormSection.findOrderedSectionMemberships(activityId, meta.getTimestamp()).get(0);
        final int sectionOrder = firstSection.getDisplayOrder() - 1;
        final long newFormSectionId = jdbiFormSection.insert(jdbiFormSection.generateUniqueCode(), null);
        jdbiFormActivityFormSection.insert(activityId, newFormSectionId, firstSection.getRevisionId(), sectionOrder);

        log.info("New section successfully created with displayOrder={} and revision={}",
                sectionOrder,
                firstSection.getRevisionId());

        // Move WHO_IS_FILLING to new section
        QuestionDto questionFillingDto =
                jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "WHO_IS_FILLING")
                        .orElseThrow(() -> new DDPException("Could not find question dto by studyId and question sid"));

        final long currFillingBlockId = helper.findQuestionBlockId(questionFillingDto.getId());
        helper.updateFormSectionBlock(newFormSectionId, currFillingBlockId);
        log.info("Question ('WHO_IS_FILLING') successfully moved to new section={}", newFormSectionId);

        // Disable CHILD_HOW_HEAR
        QuestionDto questionHowHereDto =
                jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "CHILD_HOW_HEAR")
                        .orElseThrow(() -> new DDPException("Could not find question dto by studyId and question sid"));

        final long terminatedRevId = jdbiRevision.copyAndTerminate(questionHowHereDto.getRevisionId(), meta);

        final long currHowHereBlockId = helper.findQuestionBlockId(questionHowHereDto.getId());
        questionDao.disableTextQuestion(questionHowHereDto.getId(), meta);
        helper.updateFormSectionBlockRevision(currHowHereBlockId, terminatedRevId);
        log.info("Question ('CHILD_HOW_HEAR') successfully disabled");

        // Disable CHILD_EXPERIENCE
        QuestionDto questionExperienceDto =
                jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "CHILD_EXPERIENCE")
                        .orElseThrow(() -> new DDPException("Could not find question dto by studyId and question sid"));

        final long currExperienceBlockId = helper.findQuestionBlockId(questionExperienceDto.getId());
        questionDao.disableTextQuestion(questionExperienceDto.getId(), meta);
        helper.updateFormSectionBlockRevision(currExperienceBlockId, terminatedRevId);
        log.info("Question ('CHILD_EXPERIENCE') successfully disabled");

        // Disable CHILD_RACE
        QuestionDto questionRaceDto =
                jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "CHILD_RACE")
                        .orElseThrow(() -> new DDPException("Could not find question dto by studyId and question sid"));

        final long currRaceBlockId = helper.findQuestionBlockId(questionRaceDto.getId());
        questionDao.disablePicklistQuestion(questionRaceDto.getId(), meta);
        helper.updateFormSectionBlockRevision(currRaceBlockId, terminatedRevId);
        log.info("Question ('CHILD_RACE') successfully disabled");

        // Disable CHILD_HISPANIC
        QuestionDto questionHispanicDto =
                jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "CHILD_HISPANIC")
                        .orElseThrow(() -> new DDPException("Could not find question dto by studyId and question sid"));

        final long currHispanicBlockId = helper.findQuestionBlockId(questionHispanicDto.getId());
        questionDao.disablePicklistQuestion(questionHispanicDto.getId(), meta);
        helper.updateFormSectionBlockRevision(currHispanicBlockId, terminatedRevId);
        log.info("Question ('CHILD_HISPANIC') successfully disabled");

        // Delete copy configs
        Set<Long> locationIds = helper.findCopyConfigsByQuestionSid(Set.of(
                questionHowHereDto.getId(),
                questionExperienceDto.getId(),
                questionRaceDto.getId(),
                questionHispanicDto.getId()));

        Set<Long> configPairs = helper.findCopyConfigPairsByLocIds(locationIds);

        DBUtils.checkDelete(configPairs.size(), copyConfigurationSql.deleteCopyConfigPairs(configPairs));
        DBUtils.checkDelete(locationIds.size(), copyConfigurationSql.bulkDeleteCopyLocations(locationIds));
        log.info("Copy configs successfully deleted");
        helper.updateActivityNameAndTitle(activityId, "About Your Child’s Cancer", "About Your Child’s Cancer");
        updateTranslationSummaries(handle, studyDto);
    }

    private void updateTranslationSummaries(Handle handle, StudyDto studyDto) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), ACTIVITY_CODE);
        Optional<ActivityVersionDto> opt = jdbiVersion.getActiveVersion(activityId);

        ActivityVersionDto version;
        if (opt.isPresent()) {
            version = opt.get();
        } else {
            throw new DDPException("Unable to fetch version");
        }




        List<? extends Config> configList = updatesDataCfg.getConfigList(TRANS_UPDATE);
        for (Config config : configList) {
            updateSummary(config, handle);
        }
        List<? extends Config> configList1 = updatesDataCfg.getConfigList(TRANS_INSERT);
        for (Config config : configList1) {
            insertTransSummaryText(config, handle, version);
        }
    }

    private void insertTransSummaryText(Config config, Handle handle, ActivityVersionDto version) {
        //        String oldSum = config.getString(TRANS_UPDATE_OLD);
        //        String newSum = config.getString(TRANS_UPDATE_NEW);
        String activityCode = config.getString(SUMMARY_INSERT_ACTIVITY_CODE);
        String statusTypeCode = config.getString(SUMMARY_INSERT_STATUS_TYPE_CODE);
        String languageCode = config.getString(SUMMARY_INSERT_LANGUAGE_CODE);
        String text = config.getString(SUMMARY_INSERT_TEXT);

        ActivityDto activityDto;
        Optional<ActivityDto> opt = handle.attach(JdbiActivity.class).findActivityByStudyGuidAndCode(STUDY_GUID, ACTIVITY_CODE);
        if (opt.isPresent()) {
            activityDto = opt.get();
        } else {
            throw new DDPException("Unable to get activityDto");
        }
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, version);

        handle.attach(SqlHelper.class).insertTransSummaryText(activityCode, statusTypeCode, languageCode, text);

        //        currentDef.getTranslatedSummaries()
        //                .stream().filter(sum -> sum.getText().equals(oldSum))
        //                .forEach(sum -> handle.attach(SqlHelper.class).insertTransSummaryText(activityDto.getActivityCode(),cur));
    }

    private void updateSummary(Config config, Handle handle) {
        String oldSum = String.format("%s%s%s", "%", config.getString(TRANS_UPDATE_OLD), "%");
        String newSum = config.getString(TRANS_UPDATE_NEW);

        handle.attach(SqlHelper.class).updateVarSubstitutionValue(oldSum, newSum);
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("insert into i18n_study_activity_summary_trans(\n"
                + "\tstudy_activity_id,\n"
                + "\tactivity_instance_status_type_id,\n"
                + "\tlanguage_code_id,\n"
                + "\ttranslation_text\n"
                + ")\n"
                + "select study_activity_id,\n"
                + "(select activity_instance_status_type_id  "
                + "from activity_instance_status_type "
                + "where activity_instance_status_type_code =:statusTypeCode),\n"
                + "(select language_code_id  from language_code where iso_language_code =:languageCode),\n"
                + ":text\n"
                + "from study_activity WHERE study_activity_code =:activityCode \n")
        int _insertTransSummaryByActivityCode(
                @Bind("activityCode") String activityCode,
                @Bind("statusTypeCode") String statusTypeCode,
                @Bind("languageCode") String languageCode,
                @Bind("text") String text
        );

        default void insertTransSummaryText(
                String activityCode,
                String statusTypeCode,
                String languageCode,
                String text
        ) {
            int numInserted = _insertTransSummaryByActivityCode(activityCode, statusTypeCode, languageCode, text);
            if (numInserted < 1) {
                throw new DDPException("Expected to insert a summary translation for activity="
                        + activityCode + " but inserted " + numInserted);
            }
        }



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
        Set<Long> findCopyConfigsByQuestionSid(
                @BindList(value = "questionSid", onEmpty = BindList.EmptyHandling.NULL) Set<Long> questionSid);

        @SqlQuery("select copy_configuration_pair_id from copy_configuration_pair "
                + "where source_location_id in (<locIds>) or target_location_id in (<locIds>)")
        Set<Long> findCopyConfigPairsByLocIds(
                @BindList(value = "locIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> locIds);

        @SqlUpdate("update form_section__block set revision_id = :revisionId where block_id = :blockId")
        void updateFormSectionBlockRevision(@Bind("blockId") long blockId, @Bind("revisionId") long revisionId);

        @SqlUpdate("update form_section__block set form_section_id = :formSectionId where block_id = :blockId")
        void updateFormSectionBlock(@Bind("formSectionId") long formSectionId, @Bind("blockId") long blockId);

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
    }
}
