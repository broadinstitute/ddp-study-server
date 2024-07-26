package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.time.Instant;

@Slf4j
public class PancanPrequalV2 implements CustomTask {

    private static final String OTHER_CANCER = "OTHER_CANCER";
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
    }

    private void revisionPrequal(Handle handle, StudyDto studyDto, UserDto adminUser) {
        String activityCode = "PREQUAL";
        String versionTag = "v2";
        log.info("Versioning activity: {}", activityCode);

        ActivityDao activityDao = handle.attach(ActivityDao.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        RevisionMetadata meta = makeRevMetadata(studyDto, adminUser, activityCode, versionTag);
        ActivityVersionDto v2Dto = activityDao.changeVersion(activityId, versionTag, meta);
        log.info("Created new revision {} of activity {} with revId: {}", v2Dto.getVersionTag(), activityCode, v2Dto.getRevId());

        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);
        disablePicklistOption("PRIMARY_CANCER_SELF", OTHER_CANCER, studyDto.getId(), meta, jdbiQuestion, plQuestionDao);
        disablePicklistOption("PRIMARY_CANCER_CHILD", OTHER_CANCER, studyDto.getId(), meta, jdbiQuestion, plQuestionDao);
    }

    private void disablePicklistOption(String plQuestionStableId, String optionStableId, long studyId, RevisionMetadata meta,
                                       JdbiQuestion jdbiQuestion, PicklistQuestionDao plQuestionDao) {
        QuestionDto currPLQuestionDto = jdbiQuestion
                .findLatestDtoByStudyIdAndQuestionStableId(studyId, plQuestionStableId)
                .orElseThrow(() -> new DDPException("Could not find question " + plQuestionStableId));

        plQuestionDao.disableOption(currPLQuestionDto.getId(), optionStableId, meta, true);
        log.info("Disabled picklist option '{}' from question '{}'", optionStableId, plQuestionStableId);
    }

    private RevisionMetadata makeRevMetadata(StudyDto studyDto, UserDto adminUser, String activityCode, String versionTag) {
        long start = Instant.now().toEpochMilli();
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        return new RevisionMetadata(start, adminUser.getUserId(), reason);
    }

}
