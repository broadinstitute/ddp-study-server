package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;

public class SomaticAssentAddendumV2 implements CustomTask{

    private String s;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        s= "Sf";
    }

    @Override
    public void run(Handle handle) {
        s+="asd";

        String v = s;

    }
}
