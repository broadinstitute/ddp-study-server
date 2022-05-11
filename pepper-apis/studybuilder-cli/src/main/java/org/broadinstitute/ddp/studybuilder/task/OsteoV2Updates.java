package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAboutChildV2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAboutYouV2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoActivityDetailsUpdate;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoAdultConsentFixes;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoConsentVersion2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoDdp7601;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoGovernanceFix;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoLovedOneV2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoMRFv2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoNewActivities;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoNewFamilyHistory;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoPDFv2;
import org.broadinstitute.ddp.studybuilder.task.osteo.OsteoPrequalUpdate;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        tasks.add(new OsteoGovernanceFix());
        tasks.add(new OsteoNewFamilyHistory());

        // Last
        tasks.add(new OsteoInsertEvents());
        tasks.add(new UpdateStudyWorkflows());
        tasks.forEach(t -> t.init(cfgPath, studyCfg, varsCfg));
    }

    @Override
    public void run(Handle handle) {
        tasks.forEach(t -> t.run(handle));
    }
}
