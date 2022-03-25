package org.broadinstitute.ddp.studybuilder.task.osteoupdates;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OsteoPediatricConsentV2 implements CustomTask {

    List<CustomTask> tasks = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        tasks.add(new OsteoConsentUpdate("patches/consent-assent.conf"));
        tasks.add(new OsteoConsentUpdate("patches/parental-consent.conf"));

        tasks.forEach(t -> t.init(cfgPath, studyCfg, varsCfg));
    }

    @Override
    public void run(Handle handle) {
        tasks.forEach(t -> t.run(handle));
    }
}
