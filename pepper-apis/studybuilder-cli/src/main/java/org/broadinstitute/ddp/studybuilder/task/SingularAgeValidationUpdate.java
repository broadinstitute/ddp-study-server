package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Task to replace age validations to singular activities
 */
@Slf4j
@NoArgsConstructor
public class SingularAgeValidationUpdate implements CustomTask {
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    @Override
    public void init(final Path cfgPath, final Config studyCfg, final Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals("singular")) {
            throw new DDPException("This task is only for the singular study!");
        }

        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(final Handle handle) {
        log.info("TASK::{}", SingularAgeValidationUpdate.class.getSimpleName());

        final var study = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        final var user = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));

        final var builder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, study, user.getUserId());
        updateValidations(handle, builder, "CONSENT_DEPENDENT", "patches/ddp-8554-dependent-validations.conf");
        updateValidations(handle, builder, "CONSENT_PARENTAL", "patches/ddp-8554-parental-validations.conf");
        updateValidations(handle, builder, "CONSENT_SELF", "patches/ddp-8554-self-validation.conf");

        updateValidations(handle, builder, "ADD_PARTICIPANT_PARENTAL", "patches/pepper-18-parental-validations.conf");
        updateValidations(handle, builder, "ADD_PARTICIPANT_DEPENDENT", "patches/pepper-18-dependent-validations.conf");

        updateValidations(handle, builder, "MEDICAL_RECORD_RELEASE", "patches/ddp-8705-medical-release-dob-validations.conf");
    }

    private void updateValidations(final Handle handle, final ActivityBuilder builder, final String activityCode, final String patchFile) {
        final var file = cfgPath.getParent().resolve(patchFile).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + patchFile);
        }

        final File validationToRemove = cfgPath.getParent().resolve("patches/pepper-18-validations-to-remove.conf").toFile();
        removeValidations(handle, activityCode, ConfigFactory.parseFile(validationToRemove).resolveWith(varsCfg));

        updateValidations(handle,
                builder,
                handle.attach(JdbiActivity.class)
                        .findActivityByStudyGuidAndCode(cfg.getString("study.guid"), activityCode)
                        .orElseThrow(() -> new DDPException("Activity " + activityCode + " doesn't exist")),
                ConfigFactory.parseFile(file).resolveWith(varsCfg));

        log.info("Patch {} applied", patchFile);
    }

    private void updateValidations(final Handle handle, final ActivityBuilder builder, final ActivityDto activity, final Config config) {
        final var revisionId = handle.attach(JdbiActivityVersion.class).findAllVersionsInAscendingOrder(activity.getActivityId())
                .stream()
                .findFirst()
                .map(revision -> handle.attach(JdbiRevision.class).copyStart(revision.getRevId()))
                .orElseThrow(() -> activeRevisionNotFoundError(activity.getActivityCode(), cfg.getString("study.guid")));

        builder.insertValidations(handle,
                activity.getActivityId(),
                activity.getActivityCode(),
                revisionId,
                List.copyOf(config.getConfigList("validations")));
    }

    private void removeValidations(final Handle handle, final String activityCode, final Config config) {
        List.copyOf(config.getConfigList("validations")).forEach(validation -> removeValidation(handle, activityCode, validation));
    }

    private void removeValidation(final Handle handle, final String activityCode, final Config validation) {
        handle.attach(SqlHelper.class)
                .removeValidation(activityCode, validation.getString("precondition"), validation.getString("expression"));
    }

    private DDPException activeRevisionNotFoundError(final String activityCode, final String studyGuid) {
        return new DDPException(String.format("failed to find an active revision for [activity:%s] in [study:%s]",
                activityCode,
                studyGuid));
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
                 + "AND REPLACE(REPLACE(REPLACE(REPLACE(av.expression_text, ' ', ''), CHAR(13), ''), CHAR(10), ''), CHAR(9), '') = "
                 + "    REPLACE(REPLACE(REPLACE(REPLACE(:expression, ' ', ''), CHAR(13), ''), CHAR(10), ''), CHAR(9), '') "
                 + "AND REPLACE(REPLACE(REPLACE(REPLACE(av.precondition_text, ' ', ''), CHAR(13), ''), CHAR(10), ''), CHAR(9), '') = "
                 + "    REPLACE(REPLACE(REPLACE(REPLACE(:precondition, ' ', ''), CHAR(13), ''), CHAR(10), ''), CHAR(9), '') ")
        void removeValidation(@Bind("activityCode") final String activityCode,
                              @Bind("precondition") final String precondition,
                              @Bind("expression") final String expression);
    }
}
