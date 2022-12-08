package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class GermlineEmailEventDisable implements CustomTask {
    private static final String OSTEO_STUDY_GUID = "CMI-OSTEO";
    private static final String LMS_STUDY_GUID = "cmi-lms";
    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!(studyCfg.getString("study.guid").equals(OSTEO_STUDY_GUID)
                || studyCfg.getString("study.guid").equals(LMS_STUDY_GUID))) {
            throw new DDPException("This task is only for Osteo & LMS studies! study-guid in config: "
                    + studyCfg.getString("study.guid"));
        }
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK:: Germline Email Remainder event updates  ");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class)
                .findByStudyGuid(studyCfg.getString("study.guid"));
        updateEvents(handle, studyDto.getGuid(), "GERMLINE_CONSENT_ADDENDUM");
        updateEvents(handle, studyDto.getGuid(), "GERMLINE_CONSENT_ADDENDUM_PEDIATRIC");
    }

    private void updateEvents(Handle handle, String studyGuid, String activityCode) {
        SqlHelper helper = handle.attach(GermlineEmailEventDisable.SqlHelper.class);

        List<Long> eventConfigIds = helper.findEventConfigIdByActivity(studyGuid, activityCode);
        for (Long eventConfigId : eventConfigIds) {
            int rowCount = helper.disableEventConfig(eventConfigId);
            DBUtils.checkUpdate(1, rowCount);
            log.info("Disabled event configuration  {} related to {} rowcount {}", eventConfigId, activityCode, rowCount);
            boolean isActive = helper.isEventActive(eventConfigId);
            log.info("Event {} is {} ", eventConfigId, isActive);
        }

    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("insert into expression (expression_guid, expression_text) values (:guid, :text)")
        @GetGeneratedKeys
        long insertExpression(@Bind("guid") String guid, @Bind("text") String text);

        @SqlUpdate("update event_configuration set is_active = false where event_configuration_id = :eventConfigId")
        int disableEventConfig(@Bind("eventConfigId") long eventConfigId);

        @SqlQuery("SELECT event.is_active "
                + "FROM event_configuration as event "
                + " where event.event_configuration_id = :eventConfigId")
        boolean isEventActive(@Bind("eventConfigId") long eventConfigId);

        @SqlQuery("SELECT event.event_configuration_id "
                + "FROM event_configuration as event "
                + "   JOIN umbrella_study AS study ON study.umbrella_study_id = event.umbrella_study_id "
                + "   JOIN event_action AS event_action ON event_action.event_action_id = event.event_action_id "
                + "   JOIN event_action_type AS action_type ON action_type.event_action_type_id = event_action.event_action_type_id "
                + "   JOIN event_trigger ON event_trigger.event_trigger_id = event.event_trigger_id "
                + "   JOIN activity_status_trigger ast on ast.activity_status_trigger_id = event_trigger.event_trigger_id "
                + "   join activity_instance_status_type st on st.activity_instance_status_type_id = ast.activity_instance_status_type_id"
                + "   JOIN study_activity sa on sa.study_activity_id = ast.study_activity_id "
                + "   JOIN event_trigger_type AS trigger_type ON trigger_type.event_trigger_type_id = event_trigger.event_trigger_type_id "
                + "   JOIN user_notification_event_action AS notification_action "
                + "        ON notification_action.user_notification_event_action_id = event.event_action_id "
                + "    WHERE study.guid = :studyGuid and sa.study_activity_code = :activityCode "
                + "    and st.activity_instance_status_type_code = 'COMPLETE' and event.is_active = true")
        List<Long> findEventConfigIdByActivity(@Bind("studyGuid") String studyGuid, @Bind("activityCode") String activityCode);

    }

}
