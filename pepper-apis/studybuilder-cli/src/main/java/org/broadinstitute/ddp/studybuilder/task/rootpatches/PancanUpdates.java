package org.broadinstitute.ddp.studybuilder.task.rootpatches;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.studybuilder.task.PanCanNewActivities;
import org.broadinstitute.ddp.studybuilder.task.PanCanValidationRules;
import org.broadinstitute.ddp.studybuilder.task.PancanNewCancer;
import org.broadinstitute.ddp.studybuilder.task.PancanStoolkitEventAdd;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PancanUpdates implements CustomTask {

    private List<CustomTask> taskList = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        taskList.add(new PanCanNewActivities());
        taskList.add(new PancanNewCancer());
        taskList.add(new PancanStoolkitEventAdd());
        taskList.add(new PanCanValidationRules());

        taskList.forEach(task -> task.init(cfgPath, studyCfg, varsCfg));
    }

    @Override
    public void run(Handle handle) {
        taskList.forEach(task -> task.run(handle));
    }
}
