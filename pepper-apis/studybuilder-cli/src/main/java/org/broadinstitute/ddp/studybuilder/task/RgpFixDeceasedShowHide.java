package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiBlockExpression;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;

@Slf4j
public class RgpFixDeceasedShowHide implements CustomTask {
    private static final String MOTHER_PEX = ""
            + "user.studies[\"RGP\"].forms[\"MOTHER\"].questions[\"MOTHER_CAN_PARTICIPATE\"].answers.hasOption(\"NO\")"
            + " && user.studies[\"RGP\"].forms[\"MOTHER\"].questions[\"MOTHER_DECEASED\"].answers.hasOption(\"YES\")";
    private static final String FATHER_PEX = ""
            + "user.studies[\"RGP\"].forms[\"FATHER\"].questions[\"FATHER_CAN_PARTICIPATE\"].answers.hasOption(\"NO\")"
            + " && user.studies[\"RGP\"].forms[\"FATHER\"].questions[\"FATHER_DECEASED\"].answers.hasOption(\"YES\")";

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        fixDiseasedShowHide(handle, studyDto,
                varsCfg.getString("id.act.mother"),
                varsCfg.getString("id.q.mother_deceased_dna"),
                MOTHER_PEX);
        fixDiseasedShowHide(handle, studyDto,
                varsCfg.getString("id.act.father"),
                varsCfg.getString("id.q.father_deceased_dna"),
                FATHER_PEX);
    }

    private void fixDiseasedShowHide(Handle handle, StudyDto studyDto, String activityCode, String questionStableId, String expression) {
        log.info("Working on activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto versionDto = findActivityLatestVersion(handle, activityId);
        FormActivityDef activity = findActivityDef(handle, studyDto, activityCode, versionDto.getVersionTag());

        QuestionBlockDef blockDef = null;
        for (var section : activity.getAllSections()) {
            for (var block : section.getBlocks()) {
                if (block.getBlockType() == BlockType.QUESTION) {
                    var questionBlock = (QuestionBlockDef) block;
                    var question = questionBlock.getQuestion();
                    if (questionStableId.equals(question.getStableId())) {
                        blockDef = (QuestionBlockDef) block;
                        break;
                    }
                }
            }
        }
        if (blockDef == null) {
            throw new DDPException("Could not find question block for question: " + questionStableId);
        }

        long blockId = blockDef.getBlockId();
        var exprDto = handle.attach(JdbiBlockExpression.class)
                .getActiveByBlockId(blockDef.getBlockId())
                .orElseThrow(() -> new DDPException("Could not find expression for block id " + blockId));
        log.info("Found block definition with id {} and shown expression id {}", blockId, exprDto.getExpressionId());

        DBUtils.checkUpdate(1, handle.attach(JdbiExpression.class)
                .updateById(exprDto.getExpressionId(), expression));

        log.info("Updated shown expression for block {} question {}", blockId, questionStableId);
    }

    private ActivityVersionDto findActivityLatestVersion(Handle handle, long activityId) {
        return handle.attach(JdbiActivityVersion.class)
                .getActiveVersion(activityId)
                .orElseThrow(() -> new DDPException("Could not find active version for activity " + activityId));
    }

    private FormActivityDef findActivityDef(Handle handle, StudyDto studyDto, String activityCode, String versionTag) {
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyIdAndCode(studyDto.getId(), activityCode).get();
        ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, versionTag).get();
        return (FormActivityDef) handle.attach(ActivityDao.class)
                .findDefByDtoAndVersion(activityDto, versionDto);
    }
}
