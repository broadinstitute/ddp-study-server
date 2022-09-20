package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;

/**
 * Task to update file_size for MRF_UPLOAD File Question.
 *
 */
@Slf4j
public class SingularMRFUploadUpdates implements CustomTask {
    private static final String ACTIVITY_DATA_FILE = "patches/mrf-upload-updates.conf";

    private Config studyCfg;
    private Config dataCfg;
    private StudyDto studyDto;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK:: SingularMRFUploadUpdates ");
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));

        log.info("Editing Singular study, Medical File Release Upload activity... ");
        updateMaxFileSize(handle, dataCfg.getString("question_stable_id"), dataCfg.getLong("max_file_size"));
    }

    private void updateMaxFileSize(Handle handle, String questionSid, long maxFileSize) {

        long fileQuestionId = findLatestQuestionDto(handle, questionSid).getId();
        DBUtils.checkUpdate(1, handle.attach(SqlHelper.class).updateFileQuestionMaxSize(fileQuestionId, maxFileSize));
        log.info("Successfully updated max size of file question {} to {} bytes", questionSid, maxFileSize);
    }

    private QuestionDto findLatestQuestionDto(Handle handle, String questionSid) {
        var jdbiQuestion = handle.attach(JdbiQuestion.class);
        return jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyDto.getId(), questionSid)
                .orElseThrow(() -> new DDPException("Couldn't find question with stable code " + questionSid));
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update file_question set max_file_size = :maxFileSize where question_id = :questionId")
        int updateFileQuestionMaxSize(@Bind("questionId") long questionId, @Bind("maxFileSize") long maxFileSize);

    }
}
