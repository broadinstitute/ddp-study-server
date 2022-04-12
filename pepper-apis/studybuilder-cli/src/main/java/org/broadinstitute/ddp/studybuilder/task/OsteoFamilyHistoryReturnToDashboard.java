package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiWorkflowTransition;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.WorkflowBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OsteoFamilyHistoryReturnToDashboard implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(OsteoFamilyHistoryReturnToDashboard.class);
    private static final String STUDY_GUID = "CMI-OSTEO";
    private Config cfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        SqlHelper helper = handle.attach(SqlHelper.class);
        JdbiWorkflowTransition getJdbiWorkflowTransition = handle.attach(JdbiWorkflowTransition.class);

        List<String> activityCodes = new ArrayList<>(List.of("FAMILY_HISTORY", "FAMILY_HISTORY_V2"));

        for (var activityCode : activityCodes) {
            long transitionId = helper.findWorkflowTransitionId(studyDto.getId(), activityCode, StateType.THANK_YOU.toString());
            DBUtils.checkUpdate(1, getJdbiWorkflowTransition.updateIsActiveById(transitionId, false));
            LOG.info("Disabled workflow transition from activity {} to state {}", activityCode, StateType.THANK_YOU);
        }
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select workflow_transition_id from workflow_transition"
                + " where from_state_id = "
                + " (select was.workflow_state_id from study_activity sa, workflow_activity_state was"
                + " where sa.study_activity_id = was.study_activity_id "
                + " and sa.study_activity_code = :fromActivityCode and study_id =:studyId)"
                + " and next_state_id = (select ws.workflow_state_id from workflow_state ws, workflow_state_type wst"
                + " where wst.workflow_state_type_id = ws.workflow_state_type_id "
                + "and wst.workflow_state_type_code = :toState)"
                + " and umbrella_study_id = :studyId")
        long findWorkflowTransitionId(@Bind("studyId") long studyId,
                                      @Bind("fromActivityCode") String fromActivityCode,
                                      @Bind("toState") String toState);
    }
}
