package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiCompositeQuestion;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Task to make additional edits as part of the "Brain Tumor Project" rename.
 *
 * <p>This should be ran right after the BrainRename task. This assumes that activities will have a new version from
 * the BrainRename task, so it will make edits using that as the latest version.
 */
@Slf4j
public class OsteoLovedOneV2 implements CustomTask {

    private static final String ACTIVITY_DATA_FILE = "patches/lovedone-v2.conf";
    private static final String V1_VERSION_TAG = "v1";

    private Config studyCfg;
    private Config activityDataCfg;
    private Gson gson;
    private GsonPojoValidator validator;

    private StudyDto studyDto;
    private UserDto adminUser;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.activityDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        this.gson = GsonUtil.standardGson();
        this.validator = new GsonPojoValidator();
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));
        updateActivity(handle, activityDataCfg);
    }

    private void updateActivity(Handle handle, Config activityCfg) {

        var activityDao = handle.attach(ActivityDao.class);
        var jdbiVersion = handle.attach(JdbiActivityVersion.class);

        String activityCode = activityCfg.getString("activityCode");
        String newVersionTag = activityCfg.getString("newVersionTag");
        log.info("Editing activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        FormActivityDef activity = findActivityDef(handle, activityCode, V1_VERSION_TAG);

        // Change activity version
        RevisionMetadata meta = makeActivityRevMetadata(activityCode, newVersionTag);

        ActivityVersionDto v2Dto = activityDao.changeVersion(activityId, newVersionTag, meta);
        log.info("Version {} is created with versionId={}, revisionId={}", newVersionTag, v2Dto.getId(), v2Dto.getRevId());

        ActivityVersionDto v1Dto = jdbiVersion.findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, V1_VERSION_TAG)
                .orElseThrow(() -> new DDPException("Could not find version " + V1_VERSION_TAG));
        log.info("Version {} is terminated with revisionId={}", V1_VERSION_TAG, v1Dto.getRevId());

        // insert new questions to main section at the beginning (Fist name / Last name)
        long sectionId = activity.getSections().get(0).getSectionId();
        // 14 cause of between 10 (content block) and 20 (first question)
        int displayOrder = 14;
        addNewQuestions(handle, activityId, sectionId, displayOrder, activityCfg.getConfigList("newQuestions"), v2Dto.getRevId());

        // insert new option to picklist LOVEDONE_RELATION_TO
        Config relation = activityCfg.getConfig("relation");
        changePicklistOptions(
                handle, relation.getString("stableId"), relation.getConfigList("options"), meta, v2Dto.getRevId());

        // disable child question
        disableCompositeChildQuestion(handle, activityCfg, meta);

        // inserted new question
        insertNestedBlocks(handle, activityId, activityCfg.getString("compositeParentSid"),
                activityCfg.getConfig("newNested"), v2Dto.getRevId());
    }

    private void disableCompositeChildQuestion(Handle handle, Config activityCfg, RevisionMetadata meta) {
        var questionDao = handle.attach(QuestionDao.class);
        String questionToDisableSid = activityCfg.getString("childToDisable");
        long questionToDisableId = findLatestQuestionDto(handle, questionToDisableSid).getId();
        questionDao.disableTextQuestion(questionToDisableId, meta);
        log.info("Successfully disabled child question {}", questionToDisableSid);
    }

    private RevisionMetadata makeActivityRevMetadata(String activityCode, String newVersionTag) {
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, newVersionTag);
        return RevisionMetadata.now(adminUser.getUserId(), reason);
    }

    private FormActivityDef findActivityDef(Handle handle, String activityCode, String versionTag) {

        var activityDao = handle.attach(ActivityDao.class);
        var jdbiVersion = handle.attach(JdbiActivityVersion.class);
        var jdbiActivity = handle.attach(JdbiActivity.class);

        ActivityDto activityDto = jdbiActivity
                .findActivityByStudyIdAndCode(studyDto.getId(), activityCode)
                .orElseThrow(() -> new DDPException(
                        String.format("Couldn't find activity by studyId=%s and activityCode=%s",
                                studyDto.getId(), activityCode)));
        ActivityVersionDto versionDto = jdbiVersion
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, versionTag)
                .orElseThrow(() -> new DDPException(
                        String.format("Couldn't find activity version by studyId=%s, activityCode=%s and versionTag=%s",
                                studyDto.getId(), activityCode, versionTag)));
        return (FormActivityDef) activityDao
                .findDefByDtoAndVersion(activityDto, versionDto);
    }

    private FormBlockDef parseFormBlockDef(Config sourceCfg) {
        FormBlockDef def = gson.fromJson(ConfigUtil.toJson(sourceCfg), FormBlockDef.class);
        List<JsonValidationError> errors = validator.validateAsJson(def);
        if (!errors.isEmpty()) {
            String msg = errors.stream()
                    .map(JsonValidationError::toDisplayMessage)
                    .collect(Collectors.joining(", "));
            if (def.getBlockType().equals(BlockType.QUESTION)) {
                throw new DDPException(String.format(
                        "Question definition with stableId=%s has validation errors: %s",
                        def.getQuestions().findFirst().get().getStableId(), msg));
            } else {
                throw new DDPException(String.format(
                        "%s block definition with has validation errors: %s",
                        def.getBlockType(), msg));
            }
        }
        return def;
    }

    private void addNewQuestions(Handle handle, long activityId, long sectionId, int displayOrder,
                                 List<? extends Config> newQuestionsCfg, long revision) {

        var sectionBlockDao = handle.attach(SectionBlockDao.class);

        for (var questionCfg : newQuestionsCfg) {
            FormBlockDef compositeBlock = parseFormBlockDef(questionCfg);
            sectionBlockDao.insertBlockForSection(activityId, sectionId, displayOrder++, compositeBlock, revision);
            log.info("Inserted new question {} for activityId {}, sectionId {}, displayOrder {}",
                    compositeBlock.getQuestions().findFirst().get().getStableId(),
                    activityId, sectionId, displayOrder);
        }
    }

    private void changePicklistOptions(Handle handle, String questionStableId, List<? extends Config> optionsCfg,
                                       RevisionMetadata meta, long revisionId) {

        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);
        QuestionDto questionDto = findLatestQuestionDto(handle, questionStableId);

        plQuestionDao.disableOptions(questionDto.getId(), meta);

        List<PicklistOptionDef> pickListOptions = new ArrayList<>();
        for (var optionCfg : optionsCfg) {
            pickListOptions.add(gson.fromJson(ConfigUtil.toJson(optionCfg), PicklistOptionDef.class));
        }
        plQuestionDao.insertOptions(questionDto.getId(), pickListOptions, revisionId);
    }

    private void insertNestedBlocks(Handle handle, long activityId, String compositeSid, Config childCfg, long revisionId) {

        var jdbiCompositeQuestion = handle.attach(JdbiCompositeQuestion.class);
        var questionDao = handle.attach(QuestionDao.class);

        long compositeQuestionId = findLatestQuestionDto(handle, compositeSid).getId();

        QuestionDef child = gson.fromJson(ConfigUtil.toJson(childCfg), QuestionDef.class);

        questionDao.insertQuestionByType(activityId, child, revisionId);
        jdbiCompositeQuestion.insertChildren(compositeQuestionId, List.of(child.getQuestionId()));

        log.info("Inserted child question sid {} for composite block id {} and stable code {}",
                child.getStableId(), compositeQuestionId, compositeSid);
    }

    private QuestionDto findLatestQuestionDto(Handle handle, String questionSid) {
        var jdbiQuestion = handle.attach(JdbiQuestion.class);
        return jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyDto.getId(), questionSid)
                .orElseThrow(() -> new DDPException("Couldnt find question with stable code " + questionSid));
    }
}
