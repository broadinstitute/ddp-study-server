package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
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
public class OsteoEmailReminderUpdate implements CustomTask {
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String CANCEL_EXPR_FH = "user.studies[\"CMI-OSTEO\"].forms[\"FAMILY_HISTORY_SELF\"].isStatus(\"COMPLETE\")";

    protected Config dataCfg;
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK:: Osteo Email Remainder event updates  ");
        updateEvents(handle, "FAMILY_HISTORY_SELF", CANCEL_EXPR_FH);
    }

    private void updateEvents(Handle handle, String activityCode, String cancelExpr) {
        SqlHelper helper = handle.attach(OsteoEmailReminderUpdate.SqlHelper.class);
        JdbiExpression jdbiExpr = handle.attach(JdbiExpression.class);

        //insert new cancel Expr for reminders
        List<Long> eventConfigIds = helper.findEventConfigIdByActivity(STUDY_GUID, activityCode);
        for (Long eventConfigId : eventConfigIds) {
            String guidExpr = jdbiExpr.generateUniqueGuid();
            long expressionId = helper.insertExpression(guidExpr, cancelExpr);
            helper.updateEventConfigPrecondExpr(eventConfigId, expressionId);
            log.info("Updated event configuration  {} related to {} with cancel exprId: {} ", eventConfigId, activityCode, expressionId);
        }

    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("insert into expression (expression_guid, expression_text) values (:guid, :text)")
        @GetGeneratedKeys
        long insertExpression(@Bind("guid") String guid, @Bind("text") String text);

        @SqlUpdate("update event_configuration set cancel_expression_id = :exprId where event_configuration_id = :eventConfigId")
        int updateEventConfigPrecondExpr(@Bind("eventConfigId") long eventConfigId, @Bind("exprId") long exprId);

        @SqlQuery("SELECT event.event_configuration_id "
                + "FROM event_configuration as event "
                + "   JOIN umbrella_study AS study ON study.umbrella_study_id = event.umbrella_study_id "
                + "   JOIN event_action AS event_action ON event_action.event_action_id = event.event_action_id "
                + "   JOIN event_action_type AS action_type ON action_type.event_action_type_id = event_action.event_action_type_id "
                + "   JOIN event_trigger ON event_trigger.event_trigger_id = event.event_trigger_id "
                + "   JOIN activity_status_trigger ast on ast.activity_status_trigger_id = event_trigger.event_trigger_id "
                + "   JOIN study_activity sa on sa.study_activity_id = ast.study_activity_id "
                + "   JOIN event_trigger_type AS trigger_type ON trigger_type.event_trigger_type_id = event_trigger.event_trigger_type_id "
                + "   JOIN user_notification_event_action AS notification_action "
                + "        ON notification_action.user_notification_event_action_id = event.event_action_id "
                + "    WHERE study.guid = :studyGuid and sa.study_activity_code = :activityCode "
                + "    and event.cancel_expression_id is null and event.post_delay_seconds is not null and event.is_active = true;")
        List<Long> findEventConfigIdByActivity(@Bind("studyGuid") String studyGuid, @Bind("activityCode") String activityCode);

    }

}
