package org.broadinstitute.ddp.studybuilder.task.osteo;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j
public class OsteoDobValidationsFix implements CustomTask {
    private static final String DATA_FILE = "patches/dob-validations-precondition.conf";
    private static final String STUDY = "CMI-OSTEO";
    private static final String SEARCH_PRECONDITION_KEY = "old_precondition";
    private static final String NEW_PRECONDITION_KEY = "new_precondition";
    private static final String ACTIVITY_CODE_KEY = "activityCode";
    private static final String VALIDATIONS_KEY = "validations";
    private Config dataCfg;
    private SqlHelper helper;
    private JdbiActivity jdbiActivity;

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
        jdbiActivity = handle.attach(JdbiActivity.class);
        log.info("Updating activity validations...");
        updateActivityValidations();
    }

    private void updateActivityValidations() {
        List<? extends Config> configList = dataCfg.getConfigList(VALIDATIONS_KEY);
        for (Config config : configList) {
            updateValidation(config.getString(ACTIVITY_CODE_KEY), config.getString(SEARCH_PRECONDITION_KEY),
                    config.getString(NEW_PRECONDITION_KEY));
        }
    }

    private void updateValidation(String activityCode, String searchValue, String newValue) {
        Optional<ActivityDto> activityId = jdbiActivity.findActivityByStudyGuidAndCode(STUDY, activityCode);
        if (activityId.isEmpty()) {
            throw new DDPException(String.format("Activity code '%s' not found in study '%s'", activityCode, STUDY));
        }
        int numUpdated = helper.updateValidation(activityId.get().getActivityId(), searchValue, newValue);
        if (numUpdated != 1) {
            throw new DDPException(String.format("Ambiguous validation update for activity %s", activityCode));
        }
        log.info("Updated activity validation for '{}'", activityCode);
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update activity_validation set precondition_text=:newValue "
                + "where study_activity_id=:activityId and precondition_text=:oldValue")
        int updateValidation(@Bind("activityId") long activityId, @Bind("oldValue") String searchValue,
                             @Bind("newValue") String newValue);
    }
}
