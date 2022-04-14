package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
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

@Slf4j
public class RgpVersion2 implements CustomTask {
    private static final String DATA_FILE = "patches/version2.conf";

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        var adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));
        revisionPrequal(handle, studyDto, adminUser);
        revisionEnrollment(handle, studyDto, adminUser);
    }

    private void revisionPrequal(Handle handle, StudyDto studyDto, UserDto adminUser) {
        String activityCode = varsCfg.getString("id.act.prequal");
        String versionTag = dataCfg.getString("newVersionTag");
        log.info("Working on activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto v1Dto = findActivityLatestVersion(handle, activityId);
        FormActivityDef activity = findActivityDef(handle, studyDto, activityCode, v1Dto.getVersionTag());

        var activityDao = handle.attach(ActivityDao.class);
        RevisionMetadata meta = makeRevMetadata(studyDto, adminUser, activityCode, versionTag);
        ActivityVersionDto v2Dto = activityDao.changeVersion(activityId, versionTag, meta);
        log.info("Created new revision {} of activity {}", v2Dto.getVersionTag(), activityCode);

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

        String optionStableId = dataCfg.getString("optionsToRemove.generalInfo");
        handle.attach(PicklistQuestionDao.class)
                .disableOption(questionDef.getQuestionId(), optionStableId, meta, false);

        log.info("Disabled picklist option '{}' from question '{}'", optionStableId, questionStableId);
    }

    private void revisionEnrollment(Handle handle, StudyDto studyDto, UserDto adminUser) {
        String activityCode = varsCfg.getString("id.act.enrollment");
        String versionTag = dataCfg.getString("newVersionTag");
        log.info("Working on activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto v1Dto = findActivityLatestVersion(handle, activityId);
        FormActivityDef activity = findActivityDef(handle, studyDto, activityCode, v1Dto.getVersionTag());

        var activityDao = handle.attach(ActivityDao.class);
        RevisionMetadata meta = makeRevMetadata(studyDto, adminUser, activityCode, versionTag);
        ActivityVersionDto v2Dto = activityDao.changeVersion(activityId, versionTag, meta);
        log.info("Created new revision {} of activity {}", v2Dto.getVersionTag(), activityCode);

        String relationshipStableId = varsCfg.getString("id.q.relationship");
        String testsStableId = varsCfg.getString("id.q.tests");
        PicklistQuestionDef relationshipQuestion = null;
        PicklistQuestionDef testsQuestion = null;
        for (var section : activity.getAllSections()) {
            for (var block : section.getBlocks()) {
                if (block.getBlockType() == BlockType.QUESTION) {
                    var questionBlock = (QuestionBlockDef) block;
                    var question = questionBlock.getQuestion();
                    if (relationshipStableId.equals(question.getStableId())) {
                        relationshipQuestion = (PicklistQuestionDef) question;
                    } else if (testsStableId.equals(question.getStableId())) {
                        testsQuestion = (PicklistQuestionDef) question;
                    }
                }
            }
        }
        if (relationshipQuestion == null) {
            throw new DDPException("Could not find question: " + relationshipStableId);
        }
        if (testsQuestion == null) {
            throw new DDPException("Could not find question: " + testsStableId);
        }

        var picklistDao = handle.attach(PicklistQuestionDao.class);
        for (var optionStableId : dataCfg.getStringList("optionsToRemove.relationship")) {
            picklistDao.disableOption(relationshipQuestion.getQuestionId(), optionStableId, meta, false);
            log.info("Disabled picklist option '{}' from question '{}'", optionStableId, relationshipStableId);
        }
        for (var optionStableId : dataCfg.getStringList("optionsToRemove.tests")) {
            picklistDao.disableOption(testsQuestion.getQuestionId(), optionStableId, meta, false);
            log.info("Disabled picklist option '{}' from question '{}'", optionStableId, testsStableId);
        }
    }

    private RevisionMetadata makeRevMetadata(StudyDto studyDto, UserDto adminUser, String activityCode, String versionTag) {
        Instant start = Instant.parse(dataCfg.getString("startTimestamp"));
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        return new RevisionMetadata(start.toEpochMilli(), adminUser.getUserId(), reason);
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
