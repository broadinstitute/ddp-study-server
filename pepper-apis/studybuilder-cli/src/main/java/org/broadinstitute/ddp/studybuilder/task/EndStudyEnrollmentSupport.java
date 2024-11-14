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
 * Task to delete All pending participant queued events & disable ALL events for a pepper study.
 */
@Slf4j
public abstract class EndStudyEnrollmentSupport implements CustomTask {

    private String studyGuid = null;
    public String followupActivityCode = null;
    protected Config cfg;
    protected Config varsCfg;

    public EndStudyEnrollmentSupport(String studyGuid) {
        this.studyGuid = studyGuid;
    }

    public EndStudyEnrollmentSupport(String studyGuid, String followupActivityCode) {
        this.studyGuid = studyGuid;
        this.followupActivityCode = followupActivityCode;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

        QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
        int rowCount = queuedEventDao.deleteQueuedEventsByStudyId(studyDto.getId());
        log.info("Deleted: {} queued events for study ", rowCount, studyGuid);

        //disable ALL active event configurations
        int numUpdated = handle.attach(EventDao.class).enableAllStudyEvents(studyDto.getId(), false);
        log.info("Disabled {} event configurations for study {}", numUpdated, studyGuid);

        //update followup as non on-demand/followup if followupActivityCode is provided
        if (followupActivityCode != null) {
            long activityId = handle.attach(JdbiActivity.class)
                    .findIdByStudyIdAndCode(studyDto.getId(), followupActivityCode)
                    .orElseThrow(() -> new DDPException("Could not find activity id for " + followupActivityCode));

            DBUtils.checkUpdate(1, handle.attach(EndStudyEnrollmentSupport.SqlHelper.class).updateFollowupStudyActivity(activityId));
            log.info("updated : {} ", followupActivityCode);
        }

        //revoke study angular client
        String auth0ClientId = varsCfg.getString("auth0.clientId");
        String auth0Domain = varsCfg.getString("auth0.domain");
        DBUtils.checkUpdate(1, handle.attach(JdbiClient.class).updateIsRevokedByAuth0ClientIdAndAuth0Domain(
                true, auth0ClientId, auth0Domain));
        log.info("Revoked Angular client for {} ", studyGuid);

        //disable elastic export
        DBUtils.checkUpdate(1, handle.attach(EndStudyEnrollmentSupport.SqlHelper.class).disableStudyElasticExport(studyDto.getId()));
        log.info("Disabled Elastic Export for {} ", studyGuid);
    }

    interface SqlHelper extends SqlObject {

        @SqlUpdate("update study_activity set allow_ondemand_trigger = false, is_followup = false "
                + " where study_activity_id = :studyActivityId")
        int updateFollowupStudyActivity(@Bind("studyActivityId") long studyActivityId);

        @SqlUpdate("update umbrella_study set enable_data_export = false "
                + " where umbrella_study_id = :studyId")
        int disableStudyElasticExport(@Bind("studyId") long studyId);

    }

}
