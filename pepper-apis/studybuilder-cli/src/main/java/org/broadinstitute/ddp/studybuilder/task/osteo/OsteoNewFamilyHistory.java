package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
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

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class OsteoNewFamilyHistory implements CustomTask {

    private static final String STUDY_GUID = "CMI-OSTEO";

    private Config cfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }

        this.cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        var helper = handle.attach(SqlHelper.class);
        long activityOldId = ActivityBuilder.findActivityId(handle, studyDto.getId(), "FAMILY_HISTORY");
        List<Long> events = helper.findEventsByActivityId(activityOldId);
        int numEvents = helper.disableEvents(events);
        log.info("Disabled {} events related to old family history", numEvents);
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select ec.event_configuration_id from event_configuration ec"
                + "   join event_action ea on ec.event_action_id = ea.event_action_id"
                + "   join activity_instance_creation_action aica on ea.event_action_id = aica.activity_instance_creation_action_id"
                + " where aica.study_activity_id = :activityId")
        List<Long> findEventsByActivityId(@Bind("activityId") long activityId);

        @SqlUpdate("update event_configuration set is_active = false where event_configuration_id in (<events>)")
        int disableEvents(@BindList("events") List<Long> eventId);
    }
}
