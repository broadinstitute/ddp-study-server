package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.jdbi.v3.core.Handle;

/**
 * Implementors of custom "tasks" needs to have a no-args constructor.
 */
public interface CustomTask {

    /**
     * Customize the task as needed, using the given commandline and study config.
     *
     * @param cfgPath  the path to study config file
     * @param studyCfg the study configuration
     * @param varsCfg  the secrets/variables configuration for study
     */
    void init(Path cfgPath, Config studyCfg, Config varsCfg);

    /**
     * Executes the work of the task.
     *
     * @param handle the database handle
     */
    void run(Handle handle);
}
