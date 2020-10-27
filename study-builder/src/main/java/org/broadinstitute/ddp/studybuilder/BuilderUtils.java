package org.broadinstitute.ddp.studybuilder;

import java.util.List;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.JsonValidationError;

/**
 * Various helpers for study-builder.
 */
public class BuilderUtils {

    private static final GsonPojoValidator validator = new GsonPojoValidator();
    private static final Gson gson = GsonUtil.standardGson();

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

    /**
     * Parse out a template object if present in given path.
     *
     * @param cfg  the config
     * @param path the path to look
     * @return a template object
     */
    public static Template parseTemplate(Config cfg, String path) {
        if (cfg.hasPath(path)) {
            Config tmplCfg = cfg.getConfig(path);
            return gson.fromJson(ConfigUtil.toJson(tmplCfg), Template.class);
        } else {
            return null;
        }
    }

    /**
     * Validate given template.
     *
     * @param tmpl the template object
     * @return error message string, or null
     */
    public static String validateTemplate(Template tmpl) {
        List<JsonValidationError> errors = validator.validateAsJson(tmpl);
        if (!errors.isEmpty()) {
            return GsonPojoValidator.createValidationErrorMessage(errors, ", ");
        } else {
            return null;
        }
    }

    /**
     * Convenience helper to parse and validate a template object from given config.
     */
    public static Template parseAndValidateTemplate(Config cfg, String path) {
        Template tmpl = parseTemplate(cfg, path);
        if (tmpl == null) {
            throw new DDPException(path + " is required");
        }
        String errors = validateTemplate(tmpl);
        if (errors != null) {
            throw new DDPException(path + " has validation errors: " + errors);
        }
        return tmpl;
    }
}
