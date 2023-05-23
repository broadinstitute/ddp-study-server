package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.time.Instant;

@Slf4j
public class RgpPrequalV3 implements CustomTask {

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
        var adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));
        revisionPrequal(handle, studyDto, adminUser);
        UpdateStudyWorkflows workflowsTask = new UpdateStudyWorkflows();
        workflowsTask.init(cfgPath, studyCfg, varsCfg);
        workflowsTask.run(handle);

        UpdateStudyNonSyncEvents eventsTask = new UpdateStudyNonSyncEvents();
        eventsTask.init(cfgPath, studyCfg, varsCfg);
        eventsTask.run(handle);
    }

    private void revisionPrequal(Handle handle, StudyDto studyDto, UserDto adminUser) {
        String activityCode = varsCfg.getString("id.act.prequal");
        String versionTag = "v3";
        log.info("Versioning activity: {}", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto v2Dto = findActivityLatestVersion(handle, activityId);
        FormActivityDef activity = findActivityDef(handle, studyDto, activityCode, v2Dto.getVersionTag());

        var activityDao = handle.attach(ActivityDao.class);
        RevisionMetadata meta = makeRevMetadata(studyDto, adminUser, activityCode, versionTag);
        ActivityVersionDto v3Dto = activityDao.changeVersion(activityId, versionTag, meta);
        log.info("Created new revision {} of activity {}", v3Dto.getVersionTag(), activityCode);

        String questionStableId = varsCfg.getString("id.q.general_info");
        PicklistQuestionDef questionDef = null;
        for (var section : activity.getAllSections()) {
            for (var block : section.getBlocks()) {
                if (block.getBlockType() == BlockType.QUESTION) {
                    var questionBlock = (QuestionBlockDef) block;
                    var question = questionBlock.getQuestion();
                    if (questionStableId.equals(question.getStableId())) {
                        questionDef = (PicklistQuestionDef) question;
                        break;
                    }
                }
            }
        }
        if (questionDef == null) {
            throw new DDPException("Could not find question: " + questionStableId);
        }

        String optionStableId = "ENGLISH";
        handle.attach(PicklistQuestionDao.class)
                .disableOption(questionDef.getQuestionId(), optionStableId, meta, false);

        log.info("Disabled picklist option '{}' from question '{}'", optionStableId, questionStableId);
    }

    private RevisionMetadata makeRevMetadata(StudyDto studyDto, UserDto adminUser, String activityCode, String versionTag) {
        long start = Instant.now().toEpochMilli();
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        return new RevisionMetadata(start, adminUser.getUserId(), reason);
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
