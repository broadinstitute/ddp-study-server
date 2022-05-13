package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.ActivityInstanceCreationEventAction;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;


@Slf4j
public class PrionAddPreconditionToEventUpdate implements CustomTask {

    private static final String STUDY_GUID = "PRION";
    private static final String CONSENT_SID = "PRIONCONSENT";
    private static final String MEDICAL_SID = "PRIONMEDICAL";
    private static final String expression =
            "user.studies[\"PRION\"].forms[\"PRIONCONSENT\"].questions[\"prion_consent_s7_age\"].answers.hasTrue()";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfg = studyCfg;
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        var eventDao = handle.attach(EventDao.class);
        var helper = handle.attach(SqlHelper.class);

        // Update action ACTIVITY_INSTANCE_CREATION to PRIONREQUEST
        long prionMedicalActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), MEDICAL_SID);
        long prionConsentActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), CONSENT_SID);

        var event = handle.attach(EventDao.class).getAllEventConfigurationsByStudyId(studyDto.getId()).stream()
                .filter(e -> e.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS
                        && ((ActivityStatusChangeTrigger) e.getEventTrigger()).getStudyActivityId() == prionConsentActivityId
                        && ((ActivityStatusChangeTrigger) e.getEventTrigger()).getInstanceStatusType()
                        .equals(InstanceStatusType.COMPLETE))
                .filter(e -> e.getEventActionType() == EventActionType.ACTIVITY_INSTANCE_CREATION
                        && ((ActivityInstanceCreationEventAction) e.getEventAction()).getStudyActivityId() == prionMedicalActivityId)
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find event for activity instance creation " + MEDICAL_SID));

        log.info("Founded event configuration id {}", event.getEventConfigurationId());

        long exprId = handle.attach(JdbiExpression.class).insertExpression(expression).getId();
        log.info("Added expression to database with id {} and text {}", exprId, expression);

        DBUtils.checkUpdate(1, helper.updateEventPreExprAndOrder(event.getEventConfigurationId(), exprId));
        log.info("Successfully added preconditionExpr for eventId {}: {}", exprId, expression);
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update event_configuration set precondition_expression_id = :exprId where event_configuration_id = :eventId")
        int updateEventPreExprAndOrder(@Bind("eventId") long eventId, @Bind("exprId") long exprId);
    }
}
