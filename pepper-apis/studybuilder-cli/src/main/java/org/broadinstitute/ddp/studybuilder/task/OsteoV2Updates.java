package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OsteoV2Updates implements CustomTask {

    List<CustomTask> tasks = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        tasks.add(new OsteoConsentVersion2());
        tasks.add(new OsteoDdp7601());
        tasks.add(new OsteoNewFamilyHistory());
        tasks.add(new OsteoAboutYouV2());
        tasks.add(new OsteoConsentAddendumV2());
        tasks.add(new OsteoPrequalUpdate());
        tasks.add(new OsteoNewActivities());
        tasks.add(new OsteoMRFv2());
        //OsteoAboutChildV2() must be run after OsteoNewActivities()
        tasks.add(new OsteoAboutChildV2());
        tasks.forEach(t -> t.init(cfgPath, studyCfg, varsCfg));
    }

    @Override
    public void run(Handle handle) {
        tasks.forEach(t -> t.run(handle));
    }
}
