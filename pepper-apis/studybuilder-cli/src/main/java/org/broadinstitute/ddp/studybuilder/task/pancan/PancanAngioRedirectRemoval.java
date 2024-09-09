package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;

@Slf4j
public class PancanAngioRedirectRemoval implements CustomTask {

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private SqlHelper sqlHelper;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        if (!studyDto.getGuid().equals("cmi-pancan")) {
            throw new DDPException("This task is only for the pancan study!");
        }

        sqlHelper = handle.attach(SqlHelper.class);

        //find and delete the ANGIO study redirect workflow transition
        long workflowTransitionId = sqlHelper.findPancanAngioStudyRedirectWorkflowId();
        DBUtils.checkDelete(1, sqlHelper.updatePancanAngioRedirectPex(workflowTransitionId));
        log.info("Deleted workflow transition with ID: {}", workflowTransitionId);

        //update pex expressions to remove ANGIO (C_SARCOMAS_ANGIOSARCOMA)
        int rowCount = sqlHelper.updatePancanAngioRedirectPex();
        log.info("Updated {} rows in expression table", rowCount);
    }


    private interface SqlHelper extends SqlObject {

        @SqlQuery("select trans.workflow_transition_id "
                + "  from workflow_transition as trans "
                + "  join umbrella_study as s on s.umbrella_study_id = trans.umbrella_study_id "
                + "  join workflow_state as next_state on next_state.workflow_state_id = trans.next_state_id "
                + "  join workflow_state_type as next_state_type on next_state_type.workflow_state_type_id = next_state.workflow_state_type_id "
                + "  left join workflow_activity_state as next_act_state on next_act_state.workflow_state_id = next_state.workflow_state_id "
                + "  left join expression as expr on expr.expression_id = trans.precondition_expression_id "
                + "  left join workflow_study_redirect_state as next_redirect_state on next_redirect_state.workflow_state_id = next_state.workflow_state_id "
                + " where s.guid = 'cmi-pancan' "
                + "   and trans.is_active "
                + "   and next_state_type.workflow_state_type_code = 'STUDY_REDIRECT' "
                + "   and study_guid = 'ANGIO'")
        long findPancanAngioStudyRedirectWorkflowId();

        @SqlUpdate("delete workflow_transition  "
                + "from workflow_transition "
                + "where workflow_transition_id = :workflowTransitionId")
        int updatePancanAngioRedirectPex(@Bind("workflowTransitionId") long workflowTransitionId);

        @SqlUpdate("update expression  "
                + "set expression_text = REPLACE(expression_text, '\"C_SARCOMAS_ANGIOSARCOMA\",', '')  "
                + "where expression_text like '%hasOptionStartsWith(\"C_SARCOMAS_ANGIOSARCOMA\", \"C_GASTRO_ESOPHAGEAL_CANCER\"%' ")
        int updatePancanAngioRedirectPex();

    }


}
