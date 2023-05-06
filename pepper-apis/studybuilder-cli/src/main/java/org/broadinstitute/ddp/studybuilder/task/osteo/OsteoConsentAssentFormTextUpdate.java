package org.broadinstitute.ddp.studybuilder.task.osteo;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Slf4j
public class OsteoConsentAssentFormTextUpdate implements CustomTask {
    private static final String DATA_FILE = "patches/consent-assent-form-text-update.conf";
    private static final String OSTEO_STUDY = "CMI-OSTEO";

    private static final String TRANSLATION_UPDATES = "translation-updates";
    private static final String TRANSLATION_KEY = "varName";
    private static final String TRANSLATION_NEW = "newValue";

    private Config dataCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Config studyCfg;

    private SqlHelper sqlHelper;
    private String activityCode;
    private String studyGuid;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Patch conf file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file);

        if (!studyCfg.getString("study.guid").equals(OSTEO_STUDY)) {
            throw new DDPException("This task is only for the " + OSTEO_STUDY + " study!");
        }

        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;
        this.studyCfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        sqlHelper = handle.attach(SqlHelper.class);

        activityCode = dataCfg.getString("activityCode");
        studyGuid = studyCfg.getString("study.guid");

        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        ActivityVersionDto versionDto = getVersion(handle, studyDto, activityCode);

        // Update variables' substitution_value in i18n_template_substitution table on latest version
        updateActivityVariables(handle, versionDto);
    }

    private ActivityVersionDto getVersion(Handle handle, StudyDto studyDto, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
                .getActiveVersion(activityId)
                .orElseThrow(() -> new DDPException("Could not find latest version for activity " + activityId));
        return versionDto;
    }

    private void updateActivityVariables2(Handle handle) {
        SqlHelper sqlHelper = handle.attach(SqlHelper.class);
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            long tmplVarId = sqlHelper.findTemplateVariableIdByVariableName(config.getString(TRANSLATION_KEY));
            sqlHelper.updateVarSubstitutionValue(tmplVarId, TRANSLATION_NEW);
            log.info("Updated {} template value to '{}'", tmplVarId, config.getString(TRANSLATION_NEW));
        }
    }

    private void updateActivityVariables(Handle handle, ActivityVersionDto versionDto) {
        String lang = dataCfg.getString("language");
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            JdbiVariableSubstitution jdbiSubstitution = handle.attach(JdbiVariableSubstitution.class);
            long templateVarId = sqlHelper.findTemplateVariableIdByVariableName(config.getString(TRANSLATION_KEY));
            long templateSubId = sqlHelper.findTemplateSubstitutionId(templateVarId, versionDto.getRevId(), lang);
            boolean updated = jdbiSubstitution
                    .update(templateSubId, versionDto.getRevId(), dataCfg.getString("language"), config.getString(TRANSLATION_NEW));
            if (updated) {
                log.info("Updated substitution_value in {} for activity {} i18n_template_substitution_id {} on version {}",
                        studyGuid, activityCode, templateSubId, versionDto.getVersionTag());
            } else {
                throw new DDPException(String.format("Failed to update substitution_value for id {} language {}" + templateSubId, lang));
            }
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("SELECT sub.i18n_template_substitution_id AS substitution_id "
                + "FROM i18n_template_substitution AS sub "
                + "INNER JOIN language_code AS lc ON lc.language_code_id = sub.language_code_id "
                + "WHERE sub.template_variable_id = :templateVarId AND sub.revision_id = :revisionId AND lc.iso_language_code = :language")
        Long findTemplateSubstitutionId(
                @Bind("templateVarId") long templateVarId, @Bind("revisionId") long revisionId, @Bind("language") String language);

        @SqlQuery("SELECT template_variable_id FROM template_variable WHERE variable_name = :variable_name "
                + "ORDER BY template_variable_id DESC")
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

        @SqlUpdate("update i18n_template_substitution set substitution_value = :value where template_variable_id = :id and ")
        int _updateVarValueByTemplateVarId2(@Bind("id") long templateVarId, @Bind("value") String value);

        default void updateVarSubstitutionValue2(long templateVarId, String value) {
            int numUpdated = _updateVarValueByTemplateVarId(templateVarId, value);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 template variable value for templateVarId="
                        + templateVarId + " but updated " + numUpdated);
            }
        }
    }
}

