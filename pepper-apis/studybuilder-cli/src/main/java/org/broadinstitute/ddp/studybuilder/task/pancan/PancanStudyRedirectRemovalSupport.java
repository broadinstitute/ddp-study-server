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
public class PancanStudyRedirectRemovalSupport implements CustomTask {

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private SqlHelper sqlHelper;
    private String studyGuid = null; //study guid of redirect study from pancan
    public String searchPexExpr = null;
    public String currentPexExpr = null;
    public String newPexExpr = null;


    public PancanStudyRedirectRemovalSupport(String studyGuid) {
        this.studyGuid = studyGuid;
    }

    public PancanStudyRedirectRemovalSupport(String studyGuid, String searchPexExpr, String currentPexExpr, String newPexExpr) {
        this.studyGuid = studyGuid;
        this.searchPexExpr = searchPexExpr;
        this.currentPexExpr = currentPexExpr;
        this.newPexExpr = newPexExpr;
    }

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
            throw new DDPException("This task is only for the study cmi-pancan ");
        }

        sqlHelper = handle.attach(SqlHelper.class);

        //find and delete the passed <studyGuid> redirect workflow transition from pancan
        long workflowTransitionId = sqlHelper.findPancanStudyRedirectWorkflowId(studyGuid);
        DBUtils.checkDelete(1, sqlHelper.deletePancanStudyRedirectWorkflow(workflowTransitionId));
        log.info("Deleted workflow transition with ID:{} to study: {} from pancan.", workflowTransitionId, studyGuid);

        //update pex expressions to remove studyGuid redirect
        if (searchPexExpr != null && currentPexExpr != null && newPexExpr != null) {
            log.info("Updating pex expressions. SearchPexExpr: {}, CurrentPexExpr: {}, NewPexExpr: {}",
                    searchPexExpr, currentPexExpr, newPexExpr);
            String searchExpr = String.format("%s%s%s", "%", searchPexExpr, "%").trim();
            int rowCount = sqlHelper.updatePancanStudyRedirectPex(searchExpr, currentPexExpr, newPexExpr);
            log.info("Updated {} rows in expression table", rowCount);
        }
    }


    private interface SqlHelper extends SqlObject {

        @SqlQuery("select trans.workflow_transition_id "
                + "  from workflow_transition as trans "
                + "  join umbrella_study as s on s.umbrella_study_id = trans.umbrella_study_id "
                + "  join workflow_state as next_state on next_state.workflow_state_id = trans.next_state_id "
                + "  join workflow_state_type as next_state_type on next_state_type.workflow_state_type_id "
                + " = next_state.workflow_state_type_id "
                + " join workflow_study_redirect_state as next_redirect_state on "
                + " next_redirect_state.workflow_state_id = next_state.workflow_state_id "
                + " where s.guid = 'cmi-pancan' "
                + " and trans.is_active "
                + " and next_state_type.workflow_state_type_code = 'STUDY_REDIRECT' "
                + " and study_guid = :studyGuid")
        long findPancanStudyRedirectWorkflowId(@Bind("studyGuid") String studyGuid);

        @SqlUpdate("delete workflow_transition  "
                + "from workflow_transition "
                + "where workflow_transition_id = :workflowTransitionId")
        int deletePancanStudyRedirectWorkflow(@Bind("workflowTransitionId") long workflowTransitionId);

        @SqlUpdate("update expression "
                + "set expression_text = REPLACE(expression_text, :currentPexExpr, :newPexExpr) "
                + "where expression_text like :searchPexExpr")
        int updatePancanStudyRedirectPex(@Bind("searchPexExpr") String searchPexExpr,
                                         @Bind("currentPexExpr") String currentPexExpr, @Bind("newPexExpr") String newPexExpr);

    }


}
