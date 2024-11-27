package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.studybuilder.task.util.ValidationRuleTask;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;


/**
 * Patch to add REQUIRED validation rule for PanCan questions.
 */
public class PanCanDiagnosisDateValidationRule implements CustomTask {

    private ValidationRuleTask diagnosisDateValidationRuleTask =
            new ValidationRuleTask("patches/diagnosis-date-required-validation.conf");

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        diagnosisDateValidationRuleTask.init(cfgPath, studyCfg, varsCfg);
    }

    @Override
    public void run(Handle handle) {
        diagnosisDateValidationRuleTask.run(handle);
    }
}
