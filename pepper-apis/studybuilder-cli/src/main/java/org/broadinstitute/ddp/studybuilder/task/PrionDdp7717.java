package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PrionDdp7717 implements CustomTask {

    List<CustomTask> tasks = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        tasks.add(new PrionAddPreconditionToEventUpdate());
        tasks.add(new UpdateStudyWorkflows());
        tasks.add(new UpdateActivityBaseSettings());
        tasks.forEach(t -> t.init(cfgPath, studyCfg, varsCfg));
    }

    @Override
    public void run(Handle handle) {
        tasks.forEach(t -> t.run(handle));
    }
}
