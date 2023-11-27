package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.jdbi.v3.core.Handle;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class OsteoInsertUserEnrollmentEvents extends InsertStudyEvents {

    private static final String STUDY_GUID = "CMI-OSTEO";

    public OsteoInsertUserEnrollmentEvents() {
        super(STUDY_GUID, "patches/user-enrollment-events-upd.conf");
        log.info("TASK:: OsteoInsertUserEnrollmentEvents ");
    }

    @Override
    public void run(final Handle handle) {
        disableExistingEnrollmentEvent(handle);
        super.run(handle); //insert new enrollment events
    }

    public void disableExistingEnrollmentEvent(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);

        List<EventConfiguration> events = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId());

        //Disable All existing enrolled status events
        List<EventConfiguration> enrollEvents = events.stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS)
                .filter(event -> event.getEventActionType() == EventActionType.USER_ENROLLED)
                .collect(Collectors.toList());

        for (EventConfiguration eventConf : enrollEvents) {
            DBUtils.checkUpdate(1, handle.attach(JdbiEventConfiguration.class)
                    .updateIsActiveById(eventConf.getEventConfigurationId(), false));
            log.info("Disabled USER_ENROLLED event with id {}", eventConf.getEventConfigurationId());
        }
    }

}
