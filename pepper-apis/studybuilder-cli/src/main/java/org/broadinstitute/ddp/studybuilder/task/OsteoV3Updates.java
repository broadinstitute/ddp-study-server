package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAboutYouV2NumerationUpdate;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoPdfFixes;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoReleaseV2PdfUpdates;
import org.jdbi.v3.core.Handle;

public class OsteoV3Updates implements CustomTask {

    List<CustomTask> tasks = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        tasks.add(new OsteoPrequalEmptyBlock());
        tasks.add(new OsteoAboutYouV2NumerationUpdate());
        tasks.add(new OsteoPdfFixes());
        tasks.add(new OsteoReleaseV2PdfUpdates());
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
