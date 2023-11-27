package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SingularUpdates implements CustomTask {

    List<CustomTask> tasks = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        tasks.add(new SingularFileUploadNotifications());
        tasks.add(new SingularReadonlyActivities());
        tasks.add(new SingularDeleteEmailEvents());
        tasks.add(new SingularMRFUploadUpdates());

        // Last
        tasks.add(new SingularInsertEvents());
        tasks.add(new UpdateTemplatesInPlace());
        tasks.forEach(t -> t.init(cfgPath, studyCfg, varsCfg));
    }

    @Override
    public void run(Handle handle) {
        tasks.forEach(t -> t.run(handle));
    }
}
