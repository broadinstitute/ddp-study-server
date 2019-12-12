package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackfillStudyActivityFollowup implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(BackfillStudyActivityFollowup.class);
    private Config cfg;
    private Map<String, String> studyActivtyCodes = new HashMap<>();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        cfg = studyCfg;
        studyActivtyCodes.put("ANGIO", "followupconsent");
        studyActivtyCodes.put("cmi-brain", "POSTCONSENT");
    }

    @Override
    public void run(Handle handle) {
        String studyGuid = cfg.getString("study.guid");
        BackfillStudyActivityFollowup.SqlHelper helper = handle.attach(BackfillStudyActivityFollowup.SqlHelper.class);
        helper.backfillStudyActivityFollowup(studyGuid, studyActivtyCodes.get(studyGuid));

    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update study_activity a "
                + "set a.is_followup = true "
                + "where a.study_activity_code = :activityCode "
                + "and a.study_id = ( "
                + "select s.umbrella_study_id from umbrella_study s where s.guid = :studyGuid)")
        int _updateStudyActivityFollowup(@Bind("studyGuid") String studyGuid,
                                         @Bind("activityCode") String activityCode);

        default void backfillStudyActivityFollowup(String studyGuid, String activityCode) {
            int numUpdated = _updateStudyActivityFollowup(studyGuid, activityCode);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update is_followup for 1 row with studyGuid="
                        + studyGuid + "actvivityCode = " + activityCode + " but updated " + numUpdated);
            } else {
                LOG.info(" Done: updated 1 row");
            }
        }
    }

}
