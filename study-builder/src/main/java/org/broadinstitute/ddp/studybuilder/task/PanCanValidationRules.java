package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.util.ValidationRuleTask;
import org.jdbi.v3.core.Handle;


/**
 * Patch to add validation rules for PanCan questions.
 * The following groups of validation rules are added:
 * <ul>
 *   <li>LENGTH (max=500) - for question OTHER_COMMENTS,</li>
 *   <li>LENGTH (max=100) - for firstName and lastName (several places in PanCan activities),</li>
 *   <li>UNIQUE - for cancer composite questions (3 different cancer lists).</li>
 * </ul>
 * Each of validation groups defined in the patches configuration files.
 */
public class PanCanValidationRules implements CustomTask {

    private ValidationRuleTask firstLastNameValidationRuleTask =
            new ValidationRuleTask("patches/firstname-lastname-validation.conf");
    private ValidationRuleTask otherCommentsValidationRuleTask =
            new ValidationRuleTask("patches/other-comments-validation.conf");
    private ValidationRuleTask cancerValidationRuleTask =
            new ValidationRuleTask("patches/unique-cancer-validation.conf");

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        firstLastNameValidationRuleTask.init(cfgPath, studyCfg, varsCfg);
        otherCommentsValidationRuleTask.init(cfgPath, studyCfg, varsCfg);
        cancerValidationRuleTask.init(cfgPath, studyCfg, varsCfg);
    }

    @Override
    public void run(Handle handle) {
        firstLastNameValidationRuleTask.run(handle);
        otherCommentsValidationRuleTask.run(handle);
        cancerValidationRuleTask.run(handle);
    }
}
