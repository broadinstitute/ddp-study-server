package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;
import java.util.List;

/**
 * Task to replace age validations to singular activities
 */
@Slf4j
public class SingularAgeValidationUpdate implements CustomTask {
    private static final String DATA_FILE_PARENTAL = "patches/ddp-8554-parental-validations.conf";
    private static final String DATA_FILE_SELF     = "patches/ddp-8554-self-validation.conf";
    private static final String STUDY_GUID         = "singular";

    protected Config dataParentalCfg;
    protected Config dataSelfCfg;

    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    public SingularAgeValidationUpdate() {
        super();
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }

        final var selfFile = cfgPath.getParent().resolve(DATA_FILE_SELF).toFile();
        if (!selfFile.exists()) {
            throw new DDPException("Data file is missing: " + selfFile);
        }

        final var parentalFile = cfgPath.getParent().resolve(DATA_FILE_PARENTAL).toFile();
        if (!parentalFile.exists()) {
            throw new DDPException("Data file is missing: " + DATA_FILE_PARENTAL);
        }

        this.dataParentalCfg = ConfigFactory.parseFile(parentalFile).resolveWith(varsCfg);
        this.dataSelfCfg = ConfigFactory.parseFile(selfFile).resolveWith(varsCfg);
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK::{}", SingularAgeValidationUpdate.class.getSimpleName());

        final var study = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        final var user = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));

        final var addParentalActivity = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(cfg.getString("study.guid"), "ADD_PARTICIPANT_PARENTAL")
                .orElseThrow(() -> new DDPException("Activity ADD_PARTICIPANT_PARENTAL doesn't exist"));

        final var addSelfActivity = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(cfg.getString("study.guid"), "ADD_PARTICIPANT_SELF")
                .orElseThrow(() -> new DDPException("Activity ADD_PARTICIPANT_SELF doesn't exist"));

        final var builder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, study, user.getUserId());
        updateValidations(handle, builder, addParentalActivity, dataParentalCfg);
        updateValidations(handle, builder, addSelfActivity, dataSelfCfg);
    }

    private void updateValidations(final Handle handle, final ActivityBuilder builder, final ActivityDto activity, final Config config) {
        final var activityVersion = handle.attach(JdbiActivityVersion.class)
                .getActiveVersion(activity.getActivityId())
                .orElseThrow(() -> new DDPException("Can't fetch latest activity version for " + activity.getActivityCode()));

        handle.attach(SqlHelper.class).deleteValidations(activity.getActivityId());

        builder.insertValidations(handle,
                activity.getActivityId(),
                activity.getActivityCode(),
                activityVersion.getRevId(),
                List.copyOf(config.getConfigList("validations")));
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("DELETE FROM activity_validation WHERE study_activity_id = :studyActivityId")
        void deleteValidations(@Bind("studyActivityId") long studyActivityId);
    }
}
