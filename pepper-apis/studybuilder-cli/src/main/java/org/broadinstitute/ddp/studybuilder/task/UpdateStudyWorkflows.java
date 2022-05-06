package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.studybuilder.StudyBuilder;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;


@Slf4j
public class UpdateStudyWorkflows implements CustomTask {

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfg = studyCfg;
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyBuilder studyBuilder = new StudyBuilder(cfgPath, cfg, varsCfg);
        studyBuilder.updateWorkflow(handle);
    }
}
