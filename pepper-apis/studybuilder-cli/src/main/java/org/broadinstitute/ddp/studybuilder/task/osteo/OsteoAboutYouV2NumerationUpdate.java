package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;


@Slf4j
public class OsteoAboutYouV2NumerationUpdate implements CustomTask {

    private static final String UPDATES_DATA_FILE = "patches/about-you-numeration-update.conf";
    private static final String TRANS_UPDATE = "trans-update";
    private static final String TRANS_UPDATE_OLD = "old_text";
    private static final String TRANS_UPDATE_NEW = "new_text";
    private static final String STUDY_GUID = "CMI-OSTEO";

    private Config updatesDataCfg;


    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File updatesFile = cfgPath.getParent().resolve(UPDATES_DATA_FILE).toFile();
        if (!updatesFile.exists()) {
            throw new DDPException("Data file is missing: " + updatesFile);
        }

        this.updatesDataCfg = ConfigFactory.parseFile(updatesFile);

        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
    }

    @Override
    public void run(Handle handle) {
        updateTranslationSummaries(handle);
    }

    private void updateTranslationSummaries(Handle handle) {
        List<? extends Config> configList = updatesDataCfg.getConfigList(TRANS_UPDATE);
        for (Config config : configList) {
            updateSummary(config, handle);
        }
    }

    private void updateSummary(Config config, Handle handle) {
        String oldSum = String.format("%s%s%s", "%", config.getString(TRANS_UPDATE_OLD), "%");
        String newSum = config.getString(TRANS_UPDATE_NEW);

        handle.attach(SqlHelper.class).updateVarSubstitutionValue(oldSum, newSum);
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update i18n_template_substitution set substitution_value = :newValue where substitution_value like :oldValue")
        int _updateVarValueByOldValue(@Bind("oldValue") String oldValue, @Bind("newValue") String newValue);

        default void updateVarSubstitutionValue(String oldValue, String value) {
            int numUpdated = _updateVarValueByOldValue(oldValue, value);
            if (numUpdated < 1) {
                throw new DDPException("Expected to update a template variable value for value="
                        + oldValue + " but updated " + numUpdated);
            }
        }
    }
}
