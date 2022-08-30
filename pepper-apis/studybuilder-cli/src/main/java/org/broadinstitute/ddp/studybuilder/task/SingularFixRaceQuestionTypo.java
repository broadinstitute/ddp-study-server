package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class SingularFixRaceQuestionTypo implements CustomTask {
    private static final String STUDY_GUID = "singular";
    private static final String DATA_FILE = "patches/singular-race-question-prompt-fix.conf";
    private static final String TEMPLATE_VARIABLE_NAME = "what_is_your_race_prompt_p1";
    private static final String QUESTION_STABLE_ID = "RACE";
    private static final String LANGUAGE_CODE_EN = "en";
    private static final String QUESTION_TEMPLATE_VARIABLE_SUBSTITUTION_VALUE = "UPDATE "
            + "  template_variable tv "
             + "  join question q on q.question_prompt_template_id = tv.template_id natural "
             + "  join question_stable_code qsc natural "
             + "  join language_code lc "
             + "  join study_activity sa on sa.study_activity_id = q.study_activity_id "
             + "  join i18n_template_substitution its on its.template_variable_id = tv.template_variable_id "
             + "  join umbrella_study us on us.umbrella_study_id = sa.study_id "
             + "set "
             + "  substitution_value = ?"
             + "where "
             + "  tv.variable_name = ? "
             + "  AND qsc.stable_id = ? "
             + "  AND lc.iso_language_code = ? "
             + "  AND us.guid = ?";


    private Config dataCfg;


    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
    }

    @Override
    public void run(Handle handle) {
        String newSubstitutionValue = dataCfg.getString("prompt");
        int rowsModified = handle.execute(QUESTION_TEMPLATE_VARIABLE_SUBSTITUTION_VALUE, newSubstitutionValue,
                TEMPLATE_VARIABLE_NAME, QUESTION_STABLE_ID, LANGUAGE_CODE_EN, STUDY_GUID);
        log.info("Modified number of rows: {}", rowsModified);
    }
}
