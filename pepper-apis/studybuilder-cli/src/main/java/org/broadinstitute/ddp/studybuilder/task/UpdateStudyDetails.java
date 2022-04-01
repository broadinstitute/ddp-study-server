package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.studybuilder.StudyBuilder;
import org.jdbi.v3.core.Handle;

/**
 * General task to update study details.
 */
@Slf4j
public class UpdateStudyDetails implements CustomTask {
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
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class)
                .findByStudyGuid(studyCfg.getString("study.guid"));
        var builder = new StudyBuilder(cfgPath, studyCfg, varsCfg);

        log.info("Updating {} study details...", studyDto.getGuid());
        builder.insertOrUpdateStudyDetails(handle, studyDto.getId());
    }
}
