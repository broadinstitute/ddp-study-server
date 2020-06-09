package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AngioDiagnosisYearPlaceholder implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(AngioDiagnosisYearPlaceholder.class);
    private static final String ANGIO_GUID = "ANGIO";

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(ANGIO_GUID)) {
            throw new DDPException("This is only for Angio!");
        }
    }

    @Override
    public void run(Handle handle) {
        String sql = ""
                + "update i18n_template_substitution as i18n"
                + "  join template_variable as var on var.template_variable_id = i18n.template_variable_id"
                + "  join language_code as lang on lang.language_code_id = i18n.language_code_id"
                + "  join template as tmpl on tmpl.template_id = var.template_id"
                + "  join date_question as dq on dq.placeholder_template_id = tmpl.template_id"
                + "  join question as q on q.question_id = dq.question_id"
                + "  join study_activity as act on act.study_activity_id = q.study_activity_id"
                + "  join umbrella_study as s on s.umbrella_study_id = act.study_id"
                + "   set i18n.substitution_value = :newPlaceholder"
                + " where lang.iso_language_code = :langCode"
                + "   and var.variable_name = :varName"
                + "   and s.guid = :studyGuid";
        int numUpdated = handle.createUpdate(sql)
                .bind("newPlaceholder", "Year")
                .bind("langCode", "en")
                .bind("varName", "OTHER_CANCER_LIST_YEAR_PLACEHOLDER")
                .bind("studyGuid", ANGIO_GUID)
                .execute();
        LOG.info("Update {} template substitution value for diagnosis year placeholder", numUpdated);
    }
}
