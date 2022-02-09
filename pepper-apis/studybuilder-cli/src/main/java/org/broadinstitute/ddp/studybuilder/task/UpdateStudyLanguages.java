package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.studybuilder.StudyBuilder;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * General task to update study languages.
 */
public class UpdateStudyLanguages implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateStudyLanguages.class);

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

        LOG.info("Updating {} study languages...", studyDto.getGuid());
        builder.insertOrUpdateStudyLanguages(handle, studyDto.getId());
    }
}
