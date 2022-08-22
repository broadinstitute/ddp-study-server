package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.jdbi.v3.core.Handle;

/**
 * Task for updating the IF_YOU_HAVE_HAD_ARRHYTHMIA_COMPLICATIONS (PATIENT_SURVEY)
 * and IF_YOU_HAVE_HAD_ARRHYTHMIA (ABOUT_HEALTHY) questions to mark the DO_NOT_REQUIRE
 * and DID_NOT_REQUIRED options (respectively) as exclusive.
 * 
 * See https://broadinstitute.atlassian.net/browse/DDP-8576
 */
public class SingularAboutHealthySelectLogic implements CustomTask {
    private static final String PATCH_CONF_NAME = "SingularAboutHealthySelectLogic.conf";

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void run(Handle handle) {
        // TODO Auto-generated method stub
        
    }
    
}
