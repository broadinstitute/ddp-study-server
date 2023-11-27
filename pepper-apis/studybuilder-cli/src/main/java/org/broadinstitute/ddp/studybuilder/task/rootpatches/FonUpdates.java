package org.broadinstitute.ddp.studybuilder.task.rootpatches;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FonUpdates implements CustomTask {

    private List<CustomTask> taskList = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        for (CustomTask customTask : taskList) {
            customTask.init(cfgPath, studyCfg, varsCfg);
        }
    }

    @Override
    public void run(Handle handle) {
        taskList.forEach(taskList -> taskList.run(handle));
    }
}
