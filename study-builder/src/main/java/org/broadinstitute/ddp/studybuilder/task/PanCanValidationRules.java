package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.studybuilder.task.util.ValidationRuleTask;
import org.jdbi.v3.core.Handle;


/**
 * Patch to add validation rules for PanCan questions
 */
public class PanCanValidationRules implements CustomTask {

    private ValidationRuleTask firstLastNameTask = new ValidationRuleTask("patches/firstname-lastname-validation.conf");
    private ValidationRuleTask otherCommentsTask = new ValidationRuleTask("patches/other-comments-validation.conf");
    private ValidationRuleTask cancerTask = new ValidationRuleTask("patches/unique-cancer-validation.conf");

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        firstLastNameTask.init(cfgPath, studyCfg, varsCfg);
        otherCommentsTask.init(cfgPath, studyCfg, varsCfg);
        cancerTask.init(cfgPath, studyCfg, varsCfg);
    }

    @Override
    public void run(Handle handle) {
        firstLastNameTask.run(handle);
        otherCommentsTask.run(handle);
        cancerTask.run(handle);
    }
}
