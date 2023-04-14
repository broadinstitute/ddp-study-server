package org.broadinstitute.ddp.studybuilder.task.osteo;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

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

@Slf4j
public class RaceQuestionSpellingFix implements CustomTask {

    private static final String DATA_FILE = "patches/race-question-spelling-fix.conf";
    private static final String STUDY = "CMI-OSTEO";

    private static final String TRANSLATION_UPDATES = "translation-updates";
    private static final String TRANSLATION_NEW = "newValue";
    private static final String TRANSLATION_OLD = "oldValue";
    private static final String TRANSLATION_LANG = "language";

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
        helper = handle.attach(RaceQuestionSpellingFix.SqlHelper.class);
        log.info("Fix Race question prompt in study: {} activity: {}.", STUDY, dataCfg.getString("activityCode"));
        updateQuestionPromptNote();
    }

    private void updateQuestionPromptNote() {
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            String oldValue = config.getString(TRANSLATION_OLD);
            String language = config.getString(TRANSLATION_LANG);
            List<Long> templateSubstitutionIdList = helper.findTemplateSubstitutionIdBySubstitutionValue(oldValue, language);
            templateSubstitutionIdList.forEach((id) -> helper.updateVarSubstitutionValue(id, config.getString(TRANSLATION_NEW)));
        }
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("SELECT sub.i18n_template_substitution_id AS substitution_id "
                + "FROM i18n_template_substitution AS sub "
                + "INNER JOIN language_code AS lc ON lc.language_code_id = sub.language_code_id "
                + "WHERE sub.substitution_value = :value AND lc.iso_language_code = :lang")
        List<Long> findTemplateSubstitutionIdBySubstitutionValue(@Bind("value") String value, @Bind("lang") String lang);

        @SqlUpdate("UPDATE i18n_template_substitution SET substitution_value = :value "
                + "WHERE i18n_template_substitution_id = :id")
        int _updateSubstitutionValueById(@Bind("id") long templateSubsId, @Bind("value") String value);

        default void updateVarSubstitutionValue(long templateSubsId, String value) {
            int numUpdated = _updateSubstitutionValueById(templateSubsId, value);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 substitution_value for i18n_template_substitution_id = "
                        + templateSubsId + " but updated " + numUpdated);
            }
        }
    }
}
