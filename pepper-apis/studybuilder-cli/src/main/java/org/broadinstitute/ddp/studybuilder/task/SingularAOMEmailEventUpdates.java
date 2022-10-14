package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
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
public class SingularAOMEmailEventUpdates implements CustomTask {

    private static final String DATA_FILE  = "patches/update-aom-email-events.conf";
    private static final String STUDY_GUID  = "singular";

    protected Config dataCfg;
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!STUDY_GUID.equals(studyCfg.getString("study.guid"))) {
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
        log.info("TASK:: SingularAOMEmailEventUpdates ");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        updateEmailEventsExprs(handle, studyDto);
    }

    private void updateEmailEventsExprs(Handle handle, StudyDto studyDto) {
        var helper = handle.attach(SingularAOMEmailEventUpdates.SqlHelper.class);
        if (!dataCfg.hasPath("emailEventsExpr")) {
            throw new DDPException("There is no 'emailEventsExpr' configuration.");
        }
        log.info("Updating singular SELF email events configuration expressions...");
        List<? extends Config> events = dataCfg.getConfigList("emailEventsExpr");
        for (Config eventCfg : events) {
            log.info("Updating event configuration expression for singular email : {}", eventCfg.getString("key"));
            String templateKey = varsCfg.getString("emails." + eventCfg.getString("key"));
            updateNotificationExpr(handle, studyDto, templateKey,
                    eventCfg.getString("currExpr"), eventCfg.getString("activityCode"),
                    eventCfg.getString("newExpr"), helper);
        }
        log.info("Email Event configurations (expressions) has been updated in study for AOM support{}", cfg.getString("study.guid"));
    }

    private void updateNotificationExpr(Handle handle, StudyDto studyDto, String templateKey, String currExpr, String activityCode,
                                          String newExpr, SingularAOMEmailEventUpdates.SqlHelper helper) {
        long notificationTemplateId = handle.attach(EventActionSql.class)
                .findNotificationTemplate(templateKey, "en")
                .map(NotificationTemplate::getId)
                .get();
        List<Long> eventConfigIdList = helper.findNotificationEventConfigId(studyDto.getId(), notificationTemplateId,
                activityCode, currExpr);
        for (Long eventConfigId : eventConfigIdList) {
            int rowCount = helper.updateExpressionTextById(eventConfigId, newExpr);
            DBUtils.checkUpdate(1, rowCount);
            log.info("Updated expression for event config ID : {} ", eventConfigId);
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select e.event_configuration_id from user_notification_event_action AS act "
                + " JOIN user_notification_template AS unt "
                + " ON unt.user_notification_event_action_id = act.user_notification_event_action_id "
                + " JOIN notification_template AS t ON t.notification_template_id = unt.notification_template_id "
                + " JOIN event_configuration as e on e.event_action_id = act.user_notification_event_action_id "
                + " JOIN event_action ea on e.event_action_id = ea.event_action_id "
                + " JOIN event_action_type as eat on eat.event_action_type_id = ea.event_action_type_id "
                + " JOIN activity_status_trigger ast on e.event_trigger_id = ast.activity_status_trigger_id "
                + " JOIN study_activity sa on sa.study_activity_id = ast.study_activity_id "
                + " JOIN expression as exp on exp.expression_id = e.precondition_expression_id"
                + " where t.notification_template_id = :notificationTemplateId "
                + " and e.precondition_expression_id = exp.expression_id "
                + " and exp.expression_text = :exprText "
                + " and sa.study_activity_code = :activityCode "
                + " AND e.umbrella_study_id = :studyId")
        List<Long> findNotificationEventConfigId(@Bind("studyId") long studyId,
                                                 @Bind("notificationTemplateId") long notificationTemplateId,
                                                 @Bind("activityCode") String activityCode,
                                                 @Bind("exprText") String exprText);

        @SqlUpdate("update expression as e, "
                + " (select precondition_expression_id from event_configuration ec where ec.event_configuration_id = :id) as e2"
                + " set expression_text = :text where e.expression_id = e2.precondition_expression_id")
        int updateExpressionTextById(@Bind("id") long id, @Bind("text") String text);

    }

}
