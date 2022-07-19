package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.EmailBuilder;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;


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
public class PancanEmailUpdate implements CustomTask {

    private static final String STUDY_GUID = "cmi-pancan";
    private static final String DATA_FILE = "patches/email-updates.conf";

    private Config dataCfg;
    private Config studyCfg;
    private Config varsCfg;
    private Path cfgPath;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.studyCfg = studyCfg;
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        var emailBuilder = new EmailBuilder(cfgPath, studyCfg, varsCfg);
        emailBuilder.updateForConfigs(List.copyOf(this.dataCfg.getConfigList("sendgridEmails")));


    }
}
