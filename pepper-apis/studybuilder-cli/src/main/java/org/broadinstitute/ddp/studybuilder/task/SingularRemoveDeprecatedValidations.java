package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Task to replace age validations to singular activities
 */
@Slf4j
@NoArgsConstructor
public class SingularRemoveDeprecatedValidations implements CustomTask {

    private static final String TARGET_STUDY = "singular";
    private static final String PATCH_FILE = "patches/pepper-486-deprecated-validations.conf";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;

    @Override
    public void init(final Path cfgPath, final Config studyCfg, final Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(TARGET_STUDY)) {
            throw new DDPException("This task is only for the singular study!");
        }

        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(final Handle handle) {
        log.info("TASK::{}", SingularRemoveDeprecatedValidations.class.getSimpleName());

        final var file = cfgPath.getParent().resolve(PATCH_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + PATCH_FILE);
        }

        final var validationToRemove = cfgPath.getParent().resolve(PATCH_FILE).toFile();
        final var patchConfig = ConfigFactory.parseFile(validationToRemove).resolveWith(varsCfg);

        removeValidations(handle, TARGET_STUDY, patchConfig.getString("activity"), patchConfig.getConfigList("validations"));

        log.info("Patch {} applied", PATCH_FILE);
    }

    private void removeValidations(final Handle handle,
            final String studyGuid,
            final String activityCode,
            final List<? extends Config> config) {
        config.forEach(validation -> removeValidation(handle, studyGuid, activityCode, validation));
    }

    private void removeValidation(final Handle handle, final String studyGuid, final String activityCode, final Config validation) {
        handle.attach(SqlHelper.class)
                .removeValidation(studyGuid,
                        activityCode,
                        validation.getString("precondition"),
                        validation.getString("expression"));
    }

    /**
     * This weird SQL query is required because we don't want to care about the whitespaces and line endings
     * that came from the study definition to the database. By extending the query with REPLACE statements
     * we guarantee that all spaces, tabulations and new lines will be removed from the beginning and ending
     * of the both strings: from the database and query parameter using the same algorithm
     **/
    private interface SqlHelper extends SqlObject {
        @SqlUpdate("DELETE av "
                 + "FROM activity_validation av "
                 + "JOIN study_activity sa ON av.study_activity_id = sa.study_activity_id "
                 + "WHERE sa.study_activity_code = :activityCode "
                 + "  AND sa.guid = :studyGuid "
                 + "AND REPLACE(REPLACE(REPLACE(REPLACE(av.expression_text, ' ', ''), CHAR(13), ''), CHAR(10), ''), CHAR(9), '') = "
                 + "    REPLACE(REPLACE(REPLACE(REPLACE(:expression, ' ', ''), CHAR(13), ''), CHAR(10), ''), CHAR(9), '') "
                 + "AND REPLACE(REPLACE(REPLACE(REPLACE(av.precondition_text, ' ', ''), CHAR(13), ''), CHAR(10), ''), CHAR(9), '') = "
                 + "    REPLACE(REPLACE(REPLACE(REPLACE(:precondition, ' ', ''), CHAR(13), ''), CHAR(10), ''), CHAR(9), '') ")
         void removeValidation(@Bind("studyGuid") String studyGuid,
                                @Bind("activityCode") final String activityCode,
                                @Bind("precondition") final String precondition,
                                @Bind("expression") final String expression);
    }
}
