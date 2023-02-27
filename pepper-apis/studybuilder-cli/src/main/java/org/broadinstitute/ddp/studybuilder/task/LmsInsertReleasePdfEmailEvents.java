package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class LmsInsertReleasePdfEmailEvents extends InsertStudyEvents {
    public LmsInsertReleasePdfEmailEvents() {
        super("cmi-lms", "patches/lms-release-pdfs.conf");
    }

    @Override
    public void run(final Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        disableEmailEvent(handle,  "MEDICAL_RELEASE", studyDto.getId());
        super.run(handle);
    }

    private void disableEmailEvent(Handle handle, String activityCode, long studyId) {
        long activityId = ActivityBuilder.findActivityId(handle, studyId, activityCode);

        List<EventConfiguration> emailEvents = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyId)
                .stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS)
                .filter(event -> event.getEventActionType() == EventActionType.NOTIFICATION)
                .filter(event -> StringUtils.isBlank(event.getCancelExpression()))
                .filter(event -> event.getPostDelaySeconds() == null)
                .filter(event -> {
                    ActivityStatusChangeTrigger trigger = (ActivityStatusChangeTrigger) event.getEventTrigger();
                    return trigger.getStudyActivityId() == activityId
                            && trigger.getInstanceStatusType() == InstanceStatusType.COMPLETE;
                })
                .collect(Collectors.toList());

        //should be 2 events
        log.info("Email event count: {} ", emailEvents.size());
        if (emailEvents.size() != 2) {
            log.error("Should be 2 email events for {} ", activityCode);
            throw new RuntimeException("Should be 2 email events for " + activityCode + ". found : " + emailEvents.size());
        }

        emailEvents.stream().forEach(emailEvent -> {
            DBUtils.checkUpdate(1, handle.attach(JdbiEventConfiguration.class)
                    .updateIsActiveById(emailEvent.getEventConfigurationId(), false));
            log.info("Disabled release-complete email event with id={}", emailEvent.getEventConfigurationId());
        });
    }

}
