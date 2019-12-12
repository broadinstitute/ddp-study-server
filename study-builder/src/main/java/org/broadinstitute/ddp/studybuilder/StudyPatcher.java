package org.broadinstitute.ddp.studybuilder;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

/**
 * First, initialize the study using study-builder. Then, this patcher can help apply a predefined list of "patches", or custom tasks, to
 * the study in a consistent order. Currently, this does not support incremental patching.
 */
public class StudyPatcher {

    private static final String LOG_FILENAME = "patch-log.conf";
    private static final String PATCHES_KEY = "patches";

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    public StudyPatcher(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    public void run(Handle handle) {
        String studyGuid = studyCfg.getString("study.guid");

        if (!isStudyInitialized(handle, studyGuid)) {
            log("study '%s' has not been initialized yet", studyGuid);
            return;
        }

        Config patchCfg = readPatchLogFile(studyGuid);
        if (patchCfg == null) {
            return;
        }

        List<String> patches = patchCfg.getStringList(PATCHES_KEY);
        log("applying %d patches", patches.size());

        for (String taskName : patches) {
            log("applying patch with task name '%s'", taskName);
            runPatch(handle, taskName);
        }

        log("done");
    }

    private void log(String fmt, Object... args) {
        System.out.println("[patcher] " + String.format(fmt, args));
    }

    private boolean isStudyInitialized(Handle handle, String studyGuid) {
        StudyDto dto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        return dto != null;
    }

    private Config readPatchLogFile(String studyGuid) {
        File file = cfgPath.getParent().resolve(LOG_FILENAME).toFile();
        if (!file.exists()) {
            log("no '%s' file found for study '%s', no patches applied", LOG_FILENAME, studyGuid);
            return null;
        }

        Config cfg = ConfigFactory.parseFile(file);
        if (!cfg.hasPath(PATCHES_KEY)) {
            log("'%s' is missing '%s' key, no patches applied", LOG_FILENAME, PATCHES_KEY);
            return null;
        }

        return cfg;
    }

    private void runPatch(Handle handle, String taskName) {
        CustomTask task = BuilderUtils.loadTask(taskName);
        task.init(cfgPath, studyCfg, varsCfg);
        task.run(handle);
    }
}
