package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.apache.commons.cli.ParseException;
import org.jdbi.v3.core.Handle;

/**
 * Implementors of custom "tasks" needs to have a no-args constructor.
 */
public interface CustomTask {

    /**
     * Customize the task as needed, using the given study config.
     *
     * @param cfgPath  the path to study config file
     * @param studyCfg the study configuration
     * @param varsCfg  the secrets/variables/substitutions configuration for study
     */
    void init(Path cfgPath, Config studyCfg, Config varsCfg);

    /**
     * Consume arguments provided to the custom task. These are arguments that comes after the `--` separator on the
     * command line. Tasks may choose not to implement this if it doesn't take any arguments. This is called after
     * `init()` and before `run()`.
     *
     * @param args list of arguments to the task
     */
    default void consumeArguments(String[] args) throws ParseException {
        // Not consuming by default.
    }

    /**
     * Executes the work of the task.
     *
     * @param handle the database handle
     */
    void run(Handle handle);
}
