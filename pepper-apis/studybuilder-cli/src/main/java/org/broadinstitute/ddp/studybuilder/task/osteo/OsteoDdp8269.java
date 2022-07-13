package org.broadinstitute.ddp.studybuilder.task.osteo;

import java.nio.file.Path;
import java.util.Optional;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.ActivityInstanceCreationEventAction;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;


@Slf4j
public class OsteoDdp8269 implements CustomTask {
    private static final String STUDY_GUID = "CMI-OSTEO";

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
    }

    @Override
    public void run(Handle handle) {
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);

        long consentActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), "CONSENT");
        long releaseSelfActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), "RELEASE_SELF");
        var events = handle.attach(EventDao.class).getAllEventConfigurationsByStudyId(studyDto.getId());

        EventConfiguration eventConfiguration = events.stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS)
                .filter(event -> event.getEventActionType() == EventActionType.HIDE_ACTIVITIES)
                .filter(event -> "!user.studies[\"CMI-OSTEO\"].forms[\"RELEASE_MINOR\"].hasInstance()".equals(event.getCancelExpression()))
                .filter(event -> {
                    ActivityStatusChangeTrigger trigger = (ActivityStatusChangeTrigger) event.getEventTrigger();
                    return trigger.getStudyActivityId() == consentActivityId
                            && trigger.getInstanceStatusType() == InstanceStatusType.COMPLETE;
                })
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find event"));
        long eventId = eventConfiguration.getEventConfigurationId();
        DBUtils.checkUpdate(1, handle.attach(JdbiEventConfiguration.class).updateIsActiveById(eventId, false));
        log.info("Disabled event with id={}", eventId);

        eventConfiguration = events.stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.CONSENT_SUSPENDED)
                .filter(event -> event.getEventActionType() == EventActionType.MARK_ACTIVITIES_READ_ONLY)
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find event"));
        eventId = eventConfiguration.getEventConfigurationId();
        DBUtils.checkUpdate(1, handle.attach(JdbiEventConfiguration.class).updateIsActiveById(eventId, false));
        log.info("Disabled event with id={}", eventId);

        eventConfiguration = events.stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS)
                .filter(event -> event.getEventActionType() == EventActionType.ACTIVITY_INSTANCE_CREATION)
                .filter(event -> StringUtils.isBlank(event.getCancelExpression()))
                .filter(event -> {
                    ActivityStatusChangeTrigger trigger = (ActivityStatusChangeTrigger) event.getEventTrigger();
                    return trigger.getStudyActivityId() == consentActivityId
                            && trigger.getInstanceStatusType() == InstanceStatusType.COMPLETE;
                })
                .filter(event -> {
                    ActivityInstanceCreationEventAction action = (ActivityInstanceCreationEventAction) event.getEventAction();
                    return action.getStudyActivityId() == releaseSelfActivityId;
                })
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find event"));
        eventId = eventConfiguration.getEventConfigurationId();
        var helper = handle.attach(SqlHelper.class);
        Optional<Long> exprId = helper.findEventPreconditionExprId(eventId);
        if (exprId.isPresent()) {
            var jdbiExpr = handle.attach(JdbiExpression.class);
            jdbiExpr.updateById(exprId.get(), "!user.studies[\"CMI-OSTEO\"].forms[\"CONSENT_ADDENDUM\"].hasInstance() && "
                    + "!user.studies[\"CMI-OSTEO\"].forms[\"RELEASE_MINOR\"].hasInstance()");
        } else {
            throw new DDPException("Could not found expression to update");
        }
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select precondition_expression_id from event_configuration where event_configuration_id = :id")
        Optional<Long> findEventPreconditionExprId(@Bind("id") long eventConfigurationId);
    }
}
