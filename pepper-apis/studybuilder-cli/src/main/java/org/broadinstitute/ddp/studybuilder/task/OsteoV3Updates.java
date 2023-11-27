package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAboutYouV2NumerationUpdate;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OsteoV3Updates implements CustomTask {

    List<CustomTask> tasks = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        tasks.add(new OsteoPrequalEmptyBlock());
        tasks.add(new OsteoAboutYouV2NumerationUpdate());
        tasks.add(new OsteoPdfFixes());
        tasks.add(new OsteoConsentPdfFixes());

        tasks.add(new UpdateStudyNonSyncEvents());
        tasks.add(new OsteoInsertSyncEvents());
        tasks.add(new UpdateStudyWorkflows());

        tasks.forEach(t -> t.init(cfgPath, studyCfg, varsCfg));
    }

    @Override
    public void run(Handle handle) {
        tasks.forEach(t -> t.run(handle));
    }
}
