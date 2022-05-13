package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class OsteoNewFamilyHistory implements CustomTask {

    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String DATA_FILE = "patches/family-history.conf";

    private Config cfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        this.cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        var helper = handle.attach(SqlHelper.class);
        long activityOldId = ActivityBuilder.findActivityId(handle, studyDto.getId(), "FAMILY_HISTORY");

        for (var conf : dataCfg.getConfigList("eventChanges")) {

            long eventId = helper.findEventIdByActivityIdAndLabel(activityOldId, conf.getString("label"));

            if (conf.hasPath("disable")) {

                helper.disableEvent(eventId);
                log.info("Successfully disabled event id {} with label {} study to {}",
                        eventId, conf.getString("label"), conf.getString("activityCode"));

            } else {

                long activityActionId =
                        ActivityBuilder.findActivityId(handle, studyDto.getId(), conf.getString("activityCode"));

                long activityTriggerId =
                        ActivityBuilder.findActivityId(handle, studyDto.getId(), conf.getString("triggerActivityCode"));

                helper.updateActivityInstanceCreationAction(activityActionId, eventId);
                helper.updateActivityStatusTrigger(activityTriggerId, eventId);

                log.info("Successfully updated event id {} with label {} study to {}",
                        eventId, conf.getString("label"), conf.getString("activityCode"));
            }
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select ec.event_configuration_id from event_configuration ec"
                + "   join event_action ea on ec.event_action_id = ea.event_action_id"
                + "   join activity_instance_creation_action aica on ea.event_action_id = aica.activity_instance_creation_action_id"
                + " where aica.study_activity_id = :activityId and ec.label = :label")
        int findEventIdByActivityIdAndLabel(@Bind("activityId") long activityId, @Bind("label") String label);

        @SqlUpdate("update activity_instance_creation_action "
                + "set study_activity_id = :newActivityId "
                + "where activity_instance_creation_action_id = "
                + "(select event_action_id from event_configuration where event_configuration_id = :eventConfigurationId)")
        void updateActivityInstanceCreationAction(@Bind("newActivityId") long newActivityId,
                                                  @Bind("eventConfigurationId") long eventConfigurationId);

        @SqlUpdate("update activity_status_trigger set study_activity_id = :newActivityId where activity_status_trigger_id = "
                + "(select event_trigger_id from event_configuration where event_configuration_id = :eventConfigurationId)")
        void updateActivityStatusTrigger(@Bind("newActivityId") long newActivityId,
                                         @Bind("eventConfigurationId") long eventConfigurationId);

        @SqlUpdate("update event_configuration set is_active = false where event_configuration_id = :eventId")
        int disableEvent(@BindList("eventId") long eventId);
    }
}
