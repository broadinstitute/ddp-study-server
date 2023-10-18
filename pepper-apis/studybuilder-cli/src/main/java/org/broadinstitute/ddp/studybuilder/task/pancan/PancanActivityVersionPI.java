package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.typesafe.config.Config;
import org.apache.commons.cli.ParseException;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.studybuilder.task.UpdateActivityContentSourceDB;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PancanActivityVersionPI implements CustomTask {

    private List<CustomTask> taskList = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        UpdateActivityContentSourceDB pancanUpdatesPI = new UpdateActivityContentSourceDB();
        taskList.add(pancanUpdatesPI);
        taskList.forEach(task -> task.init(cfgPath, studyCfg, varsCfg));

        try {
            pancanUpdatesPI.consumeArguments(
                    new String[]{"patches/pi-changes.conf"});
        } catch (ParseException parseException) {
            throw new DDPException(parseException.getMessage());
        }
    }

    @Override
    public void run(Handle handle) {
        taskList.forEach(task -> task.run(handle));
    }
}
