package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Slf4j
public class OsteoPrequalUpdate implements CustomTask {
    private static final String FILE = "patches/prequal-updates.conf";
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String ACTIVITY_CODE = "PREQUAL";

    private Config dataCfg;
    private Config studyCfg;
    private Gson gson;
    private Instant timestamp;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        File file = cfgPath.getParent().resolve(FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.studyCfg = studyCfg;
        this.gson = GsonUtil.standardGson();
        this.timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), ACTIVITY_CODE);
        String versionTag = dataCfg.getString("versionTag");

        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                STUDY_GUID, ACTIVITY_CODE, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), reason);
        log.info("Making revision for new changes in blocks");
        revisionPrequal(activityId, dataCfg, handle, meta, versionTag);
    }

    private void revisionPrequal(long activityId, Config dataCfg, Handle handle, RevisionMetadata meta, String versionTag) {
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, meta);
        insertBlock(handle, dataCfg, activityId, version2, meta);
        updateQuestion(handle, dataCfg, activityId);
    }

    private void insertBlock(Handle handle, Config dataCfg, long activityId, ActivityVersionDto def, RevisionMetadata revisionMetadata) {
        Config blockConfig = dataCfg.getConfig("blocksInsert").getConfig("block");
        int order = dataCfg.getConfig("blocksInsert").getInt("blockOrder");
        int sectionOrder = dataCfg.getConfig("blocksInsert").getInt("sectionOrder");

        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY_GUID, ACTIVITY_CODE).get();
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, def);
        FormSectionDef currentSectionDef = currentDef.getSections().get(sectionOrder);
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(blockConfig), FormBlockDef.class);

        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        RevisionDto revDto = RevisionDto.fromStartMetadata(def.getRevId(), revisionMetadata);

        log.info("Trying to insert new block");
        sectionBlockDao.addBlock(activityId, currentSectionDef.getSectionId(),
                order, blockDef, revDto);
    }

    private void updateQuestion(Handle handle, Config dataCfg, long activityId) {
        SqlHelper helper = handle.attach(SqlHelper.class);
        List<? extends Config> questionUpdates = dataCfg.getConfigList("questionUpdates");

        questionUpdates.forEach(config -> {
            String stableId = config.getString("stableId");
            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .findDtoByActivityIdAndQuestionStableId(activityId, stableId).get();

            if (!config.getConfig("validation").isEmpty()) {
                Config validation = config.getConfig("validation");
                String varName = String.format("%s%s%s", "%", validation.getString("varName"), "%");
                String newVal = validation.getString("newVal");
                long activityValidationTemplateId = helper.getActivityValidationTemplateId(activityId, varName);
                long templateVariableId = helper.getTemplateVariableIdbyTemplateId(activityValidationTemplateId);
                helper.updateTemplateText(newVal, templateVariableId);
            }

            List<? extends Config> question = config.getConfigList("question");
            for (Config config1 : question) {
                String subsValue = config1.getString("newVal");
                long templateVariableIdbyTemplateId = helper.getTemplateVariableIdbyTemplateId(questionDto.getPromptTemplateId());
                helper.updateTemplateText(subsValue, templateVariableIdbyTemplateId);
            }
        });

        changeQuetionStyle(handle, activityId, "PREQUAL_SELF_DESCRIBE");
        changeAgeRestriction(handle, activityId);
    }

    private void changeQuetionStyle(Handle handle, long activityId, String stableId) {
        SqlHelper helper = handle.attach(SqlHelper.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        QuestionDto questionDto = jdbiQuestion.findDtoByActivityIdAndQuestionStableId(activityId, stableId).get();
        String value = "MULTIPLE";
        PicklistSelectMode picklistSelectMode = PicklistSelectMode.valueOf(value);
        long pickListModeIdByValue = helper.getPickListModeIdByValue(picklistSelectMode);
        helper.updatePicklistOption(questionDto.getId(), pickListModeIdByValue);
    }

    private void changeAgeRestriction(Handle handle, long activityId) {
        int age = 110;
        String templateText = "Please enter an age between 0 and 110";

        String stableId1 = "SELF_CURRENT_AGE";
        String stableId2 = "CHILD_CURRENT_AGE";

        QuestionDto selfQuestion = handle.attach(JdbiQuestion.class).findDtoByActivityIdAndQuestionStableId(activityId, stableId1).get();
        QuestionDto childQuestion = handle.attach(JdbiQuestion.class).findDtoByActivityIdAndQuestionStableId(activityId, stableId2).get();

        SqlHelper helper = handle.attach(SqlHelper.class);

        var validationIds = helper.getValidationId(selfQuestion.getId());
        for (long validationId : validationIds) {
            helper.insertUpperRange(age, validationId);
            long hintTemplateId = helper.getHintTemplateId(validationId);
            long templateVariableId = helper.getTemplateVariableIdbyTemplateId(hintTemplateId);
            helper.updateTemplateText(templateText, templateVariableId);
        }

        var validationId2 = helper.getValidationId(childQuestion.getId());
        for (long validationId : validationId2) {
            helper.insertUpperRange(age, validationId);
            long hintTemplateId = helper.getHintTemplateId(validationId);
            long templateVariableId = helper.getTemplateVariableIdbyTemplateId(hintTemplateId);
            helper.updateTemplateText(templateText, templateVariableId);
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update i18n_template_substitution set substitution_value = :substitution_value"
                + " where template_variable_id = :template_variable_id")
        void updateTemplateText(@Bind("substitution_value") String value, @Bind("template_variable_id") long templateId);

        @SqlQuery("select picklist_select_mode_id from picklist_select_mode where picklist_select_mode_code = :picklist_select_mode_code")
        long getPickListModeIdByValue(@Bind("picklist_select_mode_code") PicklistSelectMode picklistSelectMode);

        @SqlUpdate("update picklist_question set picklist_select_mode_id = :picklist_select_mode_id where question_id = :question_id")
        void updatePicklistOption(@Bind("question_id") long questionId, @Bind("picklist_select_mode_id") long picklistselectModeId);

        @SqlQuery("select error_message_template_id from activity_validation "
                + "where study_activity_id = :activityId and expression_text like :text")
        long getActivityValidationTemplateId(@Bind("activityId") long activityId, @Bind("text") String text);

        @SqlQuery("select correction_hint_template_id from validation where validation_id = :validationId")
        long getHintTemplateId(@Bind("validationId")long validationId);

        @SqlQuery("select template_variable_id from template_variable where template_id = :templateId")
        long getTemplateVariableIdbyTemplateId(@Bind("templateId") long templateId);

        @SqlQuery("select question_id from question where question_stable_code_id = :stableId")
        long getQuestionId(@Bind("stableId") long stableId);

        @SqlQuery("select validation_id from question__validation where question_id = :questionId")
        List<Long> getValidationId(@Bind("questionId")long questionId);

        @SqlUpdate("update int_range_validation set max = :max where validation_id = :validationId")
        void insertUpperRange(@Bind("max") int max, @Bind("validationId") long validationId);
    }
}
