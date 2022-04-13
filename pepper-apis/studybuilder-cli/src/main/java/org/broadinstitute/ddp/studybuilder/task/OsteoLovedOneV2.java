package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import static java.util.stream.Collectors.toList;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiBlockNesting;
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
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
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

    private static final Logger LOG = LoggerFactory.getLogger(OsteoLovedOneV2.class);
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
        LOG.info("Editing activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        FormActivityDef activity = findActivityDef(handle, activityCode, V1_VERSION_TAG);

        // Change activity version
        RevisionMetadata meta = makeActivityRevMetadata(activityCode, newVersionTag);

        ActivityVersionDto v2Dto = activityDao.changeVersion(activityId, newVersionTag, meta);
        LOG.info("Version {} is created with versionId={}, revisionId={}", newVersionTag, v2Dto.getId(), v2Dto.getRevId());

        ActivityVersionDto v1Dto = jdbiVersion.findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, V1_VERSION_TAG)
                .orElseThrow(() -> new DDPException("Could not find version " + V1_VERSION_TAG));
        LOG.info("Version {} is terminated with revisionId={}", V1_VERSION_TAG, v1Dto.getRevId());

        // insert new questions to main section at the beginning (Fist name / Last name)
        long sectionId = activity.getSections().get(0).getSectionId();
        // 14 cause of between 10 (content block) and 20 (first question)
        int displayOrder = 14;
        addNewQuestions(handle, activityId, sectionId, displayOrder, activityCfg.getConfigList("newQuestions"), v2Dto.getRevId());

        // insert new option to picklist LOVEDONE_RELATION_TO
        Config relation = activityCfg.getConfig("relation");
        addPicklistOption(
                handle,
                studyDto.getId(),
                relation.getString("stableId"),
                relation.getConfigList("options"),
                5);

        // disable all component blocks
        disableAndInsertInsteadNewChild(handle, activityCfg, activityId, activity, meta, v2Dto.getRevId());
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
            LOG.info("Inserted new question {} for activityId {}, sectionId {}, displayOrder {}",
                    compositeBlock.getQuestions().findFirst().get().getStableId(),
                    activityId, sectionId, displayOrder);
        }
    }

    private void addPicklistOption(Handle handle, long studyId, String questionStableId,
                                   List<? extends Config> optionsCfg, int displayOrder) {

        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);

        QuestionDto questionDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, questionStableId)
                .orElseThrow(() -> new DDPException("Couldnt find question with stable code " + questionStableId));

        for (var optionCfg : optionsCfg) {
            PicklistOptionDef pickListOptionDef = gson.fromJson(ConfigUtil.toJson(optionCfg), PicklistOptionDef.class);
            plQuestionDao.insertOption(questionDto.getId(), pickListOptionDef, displayOrder++, questionDto.getRevisionId());
            LOG.info("Added new picklistOption " + pickListOptionDef.getStableId() + " with id "
                    + pickListOptionDef.getOptionId() + " into question " + questionDto.getStableId());
        }
    }

    private void insertNestedBlocks(Handle handle, long activityId, long compositeQuestionId, Config childCfg, long revisionId) {

        var jdbiCompositeQuestion = handle.attach(JdbiCompositeQuestion.class);
        var questionDao = handle.attach(QuestionDao.class);

        QuestionDef child = gson.fromJson(ConfigUtil.toJson(childCfg), QuestionDef.class);

        questionDao.insertQuestionByType(activityId, child, revisionId);
        jdbiCompositeQuestion.insertChildren(compositeQuestionId, List.of(child.getQuestionId()));

        LOG.info("Inserted child question sid {} for composite block id {}", child.getStableId(), compositeQuestionId);
    }

    private void disableAndInsertInsteadNewChild(Handle handle, Config activityCfg, long activityId,
                                                 FormActivityDef activity, RevisionMetadata meta, long rev) {

        var questionDao = handle.attach(QuestionDao.class);

        String controlSid = activityCfg.getString("nestedParentSid");
        String compositeSid = activityCfg.getString("compositeParentSid");
        String questionToDisableSid = activityCfg.getString("childToDisable");

        Long compositeQuestionId = null;

        for (var section : activity.getSections()) {
            for (var block : section.getBlocks()) {
                if (block.getBlockType() == BlockType.CONDITIONAL) {
                    var conditionalBlockDef = (ConditionalBlockDef) block;
                    if (conditionalBlockDef.getControl().getStableId().equals(controlSid)) {
                        for (var nested : conditionalBlockDef.getNested()) {
                            if (nested.getBlockType() == BlockType.QUESTION) {
                                QuestionDef questionDef = nested.getQuestions().findFirst().orElseThrow();
                                if (questionDef.getStableId().equals(compositeSid) &&
                                        questionDef.getQuestionType() == QuestionType.COMPOSITE) {
                                    CompositeQuestionDef compositeQuestionDef = (CompositeQuestionDef) questionDef;
                                    for (var child : compositeQuestionDef.getChildren()) {
                                        if (child.getStableId().equals(questionToDisableSid)) {
                                            compositeQuestionId = compositeQuestionDef.getQuestionId();
                                            questionDao.disableTextQuestion(child.getQuestionId(), meta);
                                            LOG.info("Successfully disabled child question {}", child.getStableId());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (compositeQuestionId == null) {
            throw new DDPException("Couldn't find question to disable with sid " + questionToDisableSid);
        }

        insertNestedBlocks(handle, activityId, compositeQuestionId, activityCfg.getConfig("newNested"), rev);
        LOG.info("Successfully inserted new child");
    }
}
