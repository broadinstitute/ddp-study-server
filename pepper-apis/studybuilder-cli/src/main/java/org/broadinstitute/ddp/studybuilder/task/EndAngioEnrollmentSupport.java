package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;

/**
 * Task to delete All pending participant queued events & disable ALL events for the Angio study.
 */
@Slf4j
public class EndAngioEnrollmentSupport implements CustomTask {

    private static final String ANGIO_STUDY = "ANGIO";
    public static final String FOLLOWUP_ACTIVITY_CODE = "followupconsent";
    protected Config cfg;
    protected Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(ANGIO_STUDY)) {
            throw new DDPException("This task is only for the " + ANGIO_STUDY + " study!");
        }
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(ANGIO_STUDY);

        QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
        int rowCount = queuedEventDao.deleteQueuedEventsByStudyId(studyDto.getId());
        log.info("Deleted: {} queued events for study ", rowCount, ANGIO_STUDY);

        //disable ALL active event configurations
        int numUpdated = handle.attach(EventDao.class).enableAllStudyEvents(studyDto.getId(), false);
        log.info("Disabled {} event configurations for study {}", numUpdated, ANGIO_STUDY);

        //update followup as non on-demand/followup
        long activityId = handle.attach(JdbiActivity.class)
                .findIdByStudyIdAndCode(studyDto.getId(), FOLLOWUP_ACTIVITY_CODE)
                .orElseThrow(() -> new DDPException("Could not find activity id for " + FOLLOWUP_ACTIVITY_CODE));

        DBUtils.checkUpdate(1, handle.attach(EndAngioEnrollmentSupport.SqlHelper.class).updateAngioStudyActivity(activityId));
        log.info("updated : {} ", FOLLOWUP_ACTIVITY_CODE);

        //revoke angio angular client
        String auth0ClientId = varsCfg.getString("auth0.clientId");
        String auth0Domain = varsCfg.getString("auth0.domain");
        DBUtils.checkUpdate(1, handle.attach(JdbiClient.class).updateIsRevokedByAuth0ClientIdAndAuth0Domain(
                true, auth0ClientId, auth0Domain));
        log.info("Revoked Angular client for {} ", ANGIO_STUDY);

        //disable elastic export
        DBUtils.checkUpdate(1, handle.attach(EndAngioEnrollmentSupport.SqlHelper.class).disableAngioStudyElasticExport(studyDto.getId()));
        log.info("Disabled Elastic Export for {} ", ANGIO_STUDY);
    }

    interface SqlHelper extends SqlObject {

        @SqlUpdate("update study_activity set allow_ondemand_trigger = false, is_followup = false "
                + " where study_activity_id = :studyActivityId")
        int updateAngioStudyActivity(@Bind("studyActivityId") long studyActivityId);

        @SqlUpdate("update umbrella_study set enable_data_export = false "
                + " where umbrella_study_id = :studyId")
        int disableAngioStudyElasticExport(@Bind("studyId") long studyId);

    }

}
