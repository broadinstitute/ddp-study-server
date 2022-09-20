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

    private Path cfgPath;
    private Config varsCfg;
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
        this.varsCfg = varsCfg;
        this.cfgPath = cfgPath;
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
        revisionPrequal(activityId, dataCfg, handle, adminUser, studyDto, meta, versionTag);

        log.info("Updating form type to " + dataCfg.getString("formType"));
        handle.attach(SqlHelper.class).updateActivityFormType(dataCfg.getString("formType"), activityId);
    }

    private void revisionPrequal(long activityId, Config dataCfg, Handle handle, User adminUser,
                                 StudyDto studyDto, RevisionMetadata meta, String versionTag) {
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, meta);
        traverseActivity(handle, dataCfg, studyDto, adminUser, activityId, version2);
        insertBlock(handle, dataCfg, activityId, version2, meta);
    }

    private void traverseActivity(Handle handle, Config dataCfg, StudyDto studyDto,
                                  User admin, long activityId, ActivityVersionDto versionDto) {
        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, admin.getId());
        ActivityDao activityDao = handle.attach(ActivityDao.class);
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        ActivityDto activityDto = jdbiActivity.findActivityByStudyIdAndCode(studyDto.getId(), ACTIVITY_CODE).get();
        Config prequal = activityBuilder.readDefinitionConfig("activities/prequal.conf");
        FormActivityDef activity = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, versionDto);
        UpdateTemplatesInPlace updateTemplatesInPlace = new UpdateTemplatesInPlace();
        updateTemplatesInPlace.traverseActivity(handle, ACTIVITY_CODE, prequal, activity, versionDto.getRevStart());
        updateQuestion(handle, dataCfg, activityId);
        changeQuetionStyle(handle, activityId, "PREQUAL_SELF_DESCRIBE");
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

    private void changeQuetionStyle(Handle handle, long activityId, String stableId) {
        SqlHelper helper = handle.attach(SqlHelper.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        QuestionDto questionDto = jdbiQuestion.findDtoByActivityIdAndQuestionStableId(activityId, stableId).get();
        String value = "MULTIPLE";
        PicklistSelectMode picklistSelectMode = PicklistSelectMode.valueOf(value);
        long pickListModeIdByValue = helper.getPickListModeIdByValue(picklistSelectMode);
        helper.updatePicklistOption(questionDto.getId(), pickListModeIdByValue);
    }

    private void updateQuestion(Handle handle, Config dataCfg, long activityId) {
        SqlHelper helper = handle.attach(SqlHelper.class);
        List<? extends Config> questionUpdates = dataCfg.getConfigList("questionUpdates");

        questionUpdates.forEach(config -> {
            if (!config.getConfig("validation").isEmpty()) {
                Config validation = config.getConfig("validation");
                String varName = String.format("%s%s%s", "%", validation.getString("varName"), "%");
                String newVal = validation.getString("newVal");
                long activityValidationTemplateId = helper.getActivityValidationTemplateId(activityId, varName);
                long templateVariableId = helper.getTemplateVariableIdbyTemplateId(activityValidationTemplateId);
                helper.updateTemplateText(newVal, templateVariableId);
            }
        });
    }


    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update i18n_template_substitution set substitution_value = :substitution_value"
                + " where template_variable_id = :template_variable_id")
        void updateTemplateText(@Bind("substitution_value") String value, @Bind("template_variable_id") long templateId);

        @SqlQuery("select error_message_template_id from activity_validation "
                + "where study_activity_id = :activityId and expression_text like :text")
        long getActivityValidationTemplateId(@Bind("activityId") long activityId, @Bind("text") String text);

        @SqlUpdate("update form_activity set form_type_id = "
                + "(select form_type_id from form_type where form_type_code = :formTypeCode) "
                + "where study_activity_id = :activityId")
        void updateActivityFormType(@Bind("formTypeCode") String formTypeCode,
                                    @Bind("activityId") long activityId);

        @SqlQuery("select template_variable_id from template_variable where template_id = :templateId")
        long getTemplateVariableIdbyTemplateId(@Bind("templateId") long templateId);

        @SqlQuery("select question_id from question where question_stable_code_id = :stableId")
        long getQuestionId(@Bind("stableId") long stableId);

        @SqlQuery("select picklist_select_mode_id from picklist_select_mode where picklist_select_mode_code = :picklist_select_mode_code")
        long getPickListModeIdByValue(@Bind("picklist_select_mode_code") PicklistSelectMode picklistSelectMode);

        @SqlUpdate("update picklist_question set picklist_select_mode_id = :picklist_select_mode_id where question_id = :question_id")
        void updatePicklistOption(@Bind("question_id") long questionId, @Bind("picklist_select_mode_id") long picklistselectModeId);
    }
}
