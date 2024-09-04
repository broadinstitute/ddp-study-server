package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;

/**
 * Task to delete All pending participant queued events & disable ALL events for the Angio study.
 */
@Slf4j
public class DeleteQueuedEventsAngio implements CustomTask {

    private static final String ANGIO_STUDY = "ANGIO";

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(ANGIO_STUDY)) {
            throw new DDPException("This task is only for the " + ANGIO_STUDY + " study!");
        }
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(ANGIO_STUDY);

        QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
        int rowCount = queuedEventDao.deleteQueuedEventsByStudyId(studyDto.getId());
        log.info("Deleted: {} queued events for study ", rowCount, ANGIO_STUDY);

        //disable ALL active event config
        int numUpdated = handle.attach(EventDao.class).enableAllStudyEvents(studyDto.getId(), false);
        log.info("Disabled {} event configurations for study {}", numUpdated, ANGIO_STUDY);
    }

}
