package org.broadinstitute.ddp.studybuilder.task.rootpatches;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.studybuilder.task.osteo.Osteo2SomaticConsentVersion3;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoConsentVersion3;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Osteo2pecgsUpdates implements CustomTask {

    private List<CustomTask> taskList = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        taskList.add(new OsteoConsentVersion3());
        taskList.add(new Osteo2SomaticConsentVersion3());

        taskList.forEach(task -> task.init(cfgPath, studyCfg, varsCfg));
    }

    @Override
    public void run(Handle handle) {
        taskList.forEach(task -> task.run(handle));
    }
}
