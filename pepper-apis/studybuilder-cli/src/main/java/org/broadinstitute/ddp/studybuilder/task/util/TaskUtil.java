package org.broadinstitute.ddp.studybuilder.task.util;

import java.io.File;
import java.nio.file.Path;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.exception.DDPException;

public class TaskUtil {

    public static Config readConfigFromFile(Path cfgPath, String confFileName, Config varsCfg) {
        File file = cfgPath.getParent().resolve(confFileName).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        return ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }
}
