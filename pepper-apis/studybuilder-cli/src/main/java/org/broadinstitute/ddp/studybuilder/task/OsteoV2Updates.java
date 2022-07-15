package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAboutYouChildTitleUpdates;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAboutChildV2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAboutYouV2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoActivityDashboardOrdering;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoActivityDetailsUpdate;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAdultConsentFixes;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoConsentVersion2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoDdp7601;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoDdp8269;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoLovedOneV2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoMRFv2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoNewActivities;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoNewFamilyHistory;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoPDFv2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoPdfUpdates;
import org.jdbi.v3.core.Handle;

public class OsteoV2Updates implements CustomTask {

    List<CustomTask> tasks = new ArrayList<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        tasks.add(new OsteoNewActivities());
        tasks.add(new OsteoConsentVersion2());
        tasks.add(new OsteoDdp7601());
        tasks.add(new OsteoAboutYouV2());
        tasks.add(new OsteoPrequalUpdate());
        tasks.add(new OsteoAboutChildV2());
        tasks.add(new OsteoMRFv2());
        tasks.add(new OsteoPDFv2());
        tasks.add(new OsteoAdultConsentFixes());
        tasks.add(new OsteoActivityDetailsUpdate());
        tasks.add(new OsteoLovedOneV2());
        tasks.add(new OsteoNewFamilyHistory());
        tasks.add(new OsteoDdp8269());
        tasks.add(new OsteoActivityDashboardOrdering());
        tasks.add(new OsteoAboutYouChildTitleUpdates());
        tasks.add(new OsteoDobValidations());

        // Last
        tasks.add(new OsteoPdfUpdates());
        tasks.add(new OsteoInsertEvents());
        tasks.add(new UpdateStudyWorkflows());

        tasks.forEach(t -> t.init(cfgPath, studyCfg, varsCfg));
    }

    @Override
    public void run(Handle handle) {
        tasks.forEach(t -> t.run(handle));
    }
}
