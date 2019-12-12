package org.broadinstitute.ddp.studybuilder;

import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;

/**
 * Various helpers for study-builder.
 */
class BuilderUtils {

    /**
     * Find and create a new instance of custom task with given name.
     *
     * @param taskName the task name
     * @return a new task instance
     */
    static CustomTask loadTask(String taskName) {
        Class<?> klass;
        try {
            klass = Class.forName("org.broadinstitute.ddp.studybuilder.task." + taskName);
        } catch (Exception e) {
            throw new DDPException("Could not find task with name=" + taskName, e);
        }

        if (!CustomTask.class.isAssignableFrom(klass)) {
            throw new DDPException("'" + taskName + "' is not a task; make sure to implement " + CustomTask.class.getName());
        }

        CustomTask task;
        try {
            task = (CustomTask) klass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new DDPException("Could not construct a task with name=" + taskName + "; make sure to have a no-args constructor", e);
        }

        return task;
    }
}
