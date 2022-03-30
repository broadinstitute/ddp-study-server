package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.CopyConfigurationSql;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.JdbiFormSection;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.FormSectionMembershipDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-off task to add adhoc symptom message to TestBoston in deployed environments.
 */
public class OsteoAboutChildV2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(OsteoAboutChildV2.class);
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String ACTIVITY_CODE = "ABOUTCHILD";
    private static final String VERSION_TAG = "v2";
    private static final String TRANSLATED_NAME = "About Your Child's Cancer";

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
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }

        this.studyCfg = studyCfg;
        this.timestamp = Instant.now();
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
        JdbiUmbrellaStudy jdbiUmbrellaStudy = handle.attach(JdbiUmbrellaStudy.class);
        UserDao userDao = handle.attach(UserDao.class);

        var studyDto = jdbiUmbrellaStudy.findByStudyGuid(studyCfg.getString("study.guid"));
        var adminUser = userDao.findUserByGuid(studyCfg.getString("adminUser.guid"))
                .orElseThrow(() -> new DDPException("Could not find admin user by guid"));

        studyId = studyDto.getId();
        var studyGuid = studyDto.getGuid();
        long activityId = ActivityBuilder.findActivityId(handle, studyId, ACTIVITY_CODE);

        meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyGuid, ACTIVITY_CODE, VERSION_TAG));

        //change version
        activityDao.changeVersion(activityId, VERSION_TAG, meta);

        //update translatedName
        updateActivityName(activityId, ACTIVITY_CODE, TRANSLATED_NAME);

        //add new section
        var section = jdbiFormActivityFormSection.findOrderedSectionMemberships(activityId, meta.getTimestamp()).get(0);
        long newFormSectionId = createSectionBefore(activityId, section);

        // Move WHO_IS_FILLING to new section
        moveQuestionToAnotherSection("WHO_IS_FILLING", newFormSectionId);

        // Delete copy configs
        List<String> questionsToDisable = new ArrayList<>(List.of(
                "CHILD_HOW_HEAR",
                "CHILD_EXPERIENCE",
                "CHILD_RACE",
                "CHILD_HISPANIC"));
        deleteCopyConfigs(questionsToDisable);

        // Disable questions
        long terminatedRevId = jdbiRevision.copyAndTerminate(section.getRevisionId(), meta);
        questionsToDisable.forEach(s -> disableQuestionDto(s, terminatedRevId));
    }

    private long createSectionBefore(long activityId, FormSectionMembershipDto beforeSection) {
        final int sectionOrder = beforeSection.getDisplayOrder() - 1;
        final long newFormSectionId = jdbiFormSection.insert(jdbiFormSection.generateUniqueCode(), null);
        jdbiFormActivityFormSection.insert(activityId, newFormSectionId, beforeSection.getRevisionId(), sectionOrder);

        LOG.info("New section successfully created with displayOrder={} and revision={}",
                sectionOrder,
                beforeSection.getRevisionId());

        return newFormSectionId;
    }

    private void moveQuestionToAnotherSection(String questionSid, long newFormSectionId) {
        QuestionDto questionFillingDto = findQuestionBySid(questionSid);

        final long currFillingBlockId = helper.findQuestionBlockId(questionFillingDto.getId());
        helper.updateFormSectionBlock(newFormSectionId, currFillingBlockId);
        LOG.info("Question ('{}') successfully moved to new section={}", questionSid, newFormSectionId);
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

        LOG.info("Question ('{}') successfully disabled", questionSid);
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
        LOG.info("Copy configs successfully deleted");
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
        LOG.info("Updated translatedName for activity {}", activityCode);
    }

    private interface SqlHelper extends SqlObject {
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

        @SqlUpdate("update form_section__block set form_section_id = :formSectionId where block_id = :blockId")
        void updateFormSectionBlock(@Bind("formSectionId") long formSectionId, @Bind("blockId") long blockId);
    }
}
