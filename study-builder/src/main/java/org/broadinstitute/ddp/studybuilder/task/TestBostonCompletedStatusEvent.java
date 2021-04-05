package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-off task to add event for setting enrollment status to COMPLETED.
 */
public class TestBostonCompletedStatusEvent implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(TestBostonCompletedStatusEvent.class);
    private static final String STUDY_GUID = "testboston";

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var eventBuilder = new EventBuilder(studyCfg, studyDto, adminUser.getId());

        List<EventConfiguration> events = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId());

        EventConfiguration firstEnrolledEvent = events.stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.USER_FIRST_ENROLLED)
                .filter(event -> event.getEventActionType() == EventActionType.ENROLLMENT_COMPLETED)
                .findFirst().orElse(null);
        if (firstEnrolledEvent == null) {
            Config eventCfg = studyCfg.getConfigList("events").stream()
                    .filter(event -> "USER_FIRST_ENROLLED".equals(event.getString("trigger.type")))
                    .filter(event -> "ENROLLMENT_COMPLETED".equals(event.getString("action.type")))
                    .findFirst().orElseThrow(() -> new DDPException("Could not find USER_FIRST_ENROLLED event in study config"));
            eventBuilder.insertEvent(handle, eventCfg);
        } else {
            LOG.info("Already has USER_FIRST_ENROLLED event configuration with id {}", firstEnrolledEvent.getEventConfigurationId());
        }
    }
}
