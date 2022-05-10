package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class OsteoAdultConsentFixes implements CustomTask {
    private static final String DATA_FILE = "patches/self-consent-update.conf";
    private static final String STUDY = "CMI-OSTEO";

    private static final String TRANSLATION_UPDATES = "translation-updates";
    private static final String TRANSLATION_KEY = "variableName";
    private static final String TRANSLATION_NEW = "newValue";

    private Config dataCfg;
    private SqlHelper helper;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        if (!studyCfg.getString("study.guid").equals(STUDY)) {
            throw new DDPException("This task is only for the " + STUDY + " study!");
        }
    }

    @Override
    public void run(Handle handle) {
        helper = handle.attach(SqlHelper.class);

        log.info("Updating activity {} variables...", dataCfg.getString("activityCode"));
        updateVariables();
    }

    private void updateVariables() {
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            revisionVariableTranslation(
                    config.getString(TRANSLATION_KEY), config.getString(TRANSLATION_NEW));
        }
    }

    private void revisionVariableTranslation(String varName, String newTemplateText) {
        long tmplVarId = helper.findTemplateVariableIdByVariableName(varName);
        helper.updateVarSubstitutionValue(tmplVarId, newTemplateText);
        log.info("Updated {} template value to '{}'", tmplVarId, newTemplateText);
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);

        @SqlUpdate("update i18n_template_substitution set substitution_value = :value where template_variable_id = :id")
        int _updateVarValueByTemplateVarId(@Bind("id") long templateVarId, @Bind("value") String value);

        default void updateVarSubstitutionValue(long templateVarId, String value) {
            int numUpdated = _updateVarValueByTemplateVarId(templateVarId, value);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 template variable value for templateVarId="
                        + templateVarId + " but updated " + numUpdated);
            }
        }
    }
}
