package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;

@Slf4j
public class SingularUpdateActivitiesToUseDateFirstCompleted implements CustomTask {
    private static final String STUDY_GUID = "singular";
    private static final String EXISTING_DATE_TEMPLATE_PATTERN = "%$ddp.date%";
    private static final String DATA_FILE = "patches/newFirstCreatedAtDateTemplateString.conf";

    private static final int EXPECTED_NUM_ROWS_TO_BE_MODIFIED = 12;
    private static final String QUESTION_TEMPLATE_VARIABLE_SUBSTITUTION_VALUE = "UPDATE template t"
            + " JOIN block_content bc ON bc.body_template_id=t.template_id"
            + " JOIN form_section__block fsb ON fsb.block_id=bc.block_id"
            + " JOIN form_section fs ON fsb.form_section_id=fs.form_section_id"
            + " JOIN form_activity__form_section fafs ON fs.form_section_id=fafs.form_section_id"
            + " JOIN form_activity fa ON fafs.form_activity_id = fa.study_activity_id"
            + " JOIN study_activity sa ON fa.study_activity_id = sa.study_activity_id"
            + " JOIN umbrella_study us ON sa.study_id=us.umbrella_study_id"
            + " SET t.template_text=?"
            + " WHERE"
            + " us.guid = ?"
            + " AND t.template_text LIKE ?";


    private Config dataCfg;


    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }

        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        String newSubstitutionValue = dataCfg.getString("templateText");
        if (StringUtils.isNotBlank(newSubstitutionValue)) {
            int rowsModified = handle.execute(QUESTION_TEMPLATE_VARIABLE_SUBSTITUTION_VALUE, newSubstitutionValue,
                    STUDY_GUID, EXISTING_DATE_TEMPLATE_PATTERN);
            log.info("Modified number of rows: {}", rowsModified);
            if (rowsModified != EXPECTED_NUM_ROWS_TO_BE_MODIFIED) {
                throw new DDPException("Was expecting exactly: " + EXPECTED_NUM_ROWS_TO_BE_MODIFIED + " Rolling back");
            }
        } else {
            throw new DDPException("Could not find the template text in configuration file");
        }

    }
}
