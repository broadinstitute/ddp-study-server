package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.EventActionSql;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.event.NotificationTemplate;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class SingularEmailEventUpdates implements CustomTask {

    private static final String DATA_FILE  = "patches/update-email-delaySeconds.conf";
    private static final String STUDY_GUID  = "singular";

    protected Config dataCfg;
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

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
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK:: SingularEmailEventUpdates ");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        updateEmailEvents(handle, studyDto);
    }

    private void updateNotificationAction(Handle handle, StudyDto studyDto, String templateKey, String currDelaySeconds,
                                          String newDelaySeconds, SingularEmailEventUpdates.SqlHelper helper) {
        long notificationTemplateId = handle.attach(EventActionSql.class)
                .findNotificationTemplate(templateKey, "en")
                .map(NotificationTemplate::getId)
                .get();

        //update postDelaySeconds
        long currPostDelaySeconds = Long.valueOf(currDelaySeconds);
        long newPostDelaySeconds = Long.valueOf(newDelaySeconds);
        long eventConfigId = helper.findNotificationEventConfigId(studyDto.getId(), notificationTemplateId, currPostDelaySeconds);
        int rowCount = helper.updateDelayToEvent(eventConfigId, newPostDelaySeconds);
        if (rowCount != 1) {
            throw new DDPException("Updated : " + rowCount + " event configurations !!" );
        }
        log.info("Updated postDelaySeconds for event config ID : {} ", eventConfigId);
    }

    private void updateEmailEvents(Handle handle, StudyDto studyDto) {
        var helper = handle.attach(SingularEmailEventUpdates.SqlHelper.class);
        if (!dataCfg.hasPath("emailEvents")) {
            throw new DDPException("There is no 'emailEvents' configuration.");
        }
        log.info("Updating singular email events configuration postDelaySeconds...");
        List<? extends Config> events = dataCfg.getConfigList("emailEvents");
        for (Config eventCfg : events) {
            log.info("Updating event configuration postDelaySeconds for singular email : {}", eventCfg.getString("key"));
            String templateKey = varsCfg.getString("emails." + eventCfg.getString("key"));
            updateNotificationAction(handle, studyDto, templateKey,
                    eventCfg.getString("currPostDelaySeconds"), eventCfg.getString("newPostDelaySeconds"), helper);
        }
        log.info("Email Event configurations (postDelaySeconds) has been updated in study {}", cfg.getString("study.guid"));
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select e.event_configuration_id from user_notification_event_action AS act "
                + " JOIN user_notification_template AS unt "
                + " ON unt.user_notification_event_action_id = act.user_notification_event_action_id "
                + " JOIN notification_template AS t ON t.notification_template_id = unt.notification_template_id "
                + " JOIN event_configuration as e on e.event_action_id = act.user_notification_event_action_id "
                + " where t.notification_template_id = :notificationTemplateId "
                + " AND e.post_delay_seconds = :postDelaySeconds "
                + " AND e.umbrella_study_id = :studyId")
        long findNotificationEventConfigId(@Bind("studyId") long studyId,
                                           @Bind("notificationTemplateId") long notificationTemplateId,
                                           @Bind("postDelaySeconds") long postDelaySeconds);

        @SqlUpdate("update event_configuration ec set ec.post_delay_seconds = :postDelaySeconds"
                + " where ec.event_configuration_id = :eventConfigId")
        int updateDelayToEvent(@Bind("eventConfigId") long eventConfigId, @Bind("postDelaySeconds") long postDelaySeconds);
    }

}
