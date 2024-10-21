package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.WorkflowBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class PancanOsteoRedirectUpdates implements CustomTask {

    private static final String DATA_FILE = "patches/osteo-redirect-workflows.conf";
    private static final String DATA_FILE_2 = "patches/osteo-redirect-block-pex.conf";
    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Config dataCfg;
    private SqlHelper sqlHelper;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        File pexFile = cfgPath.getParent().resolve(DATA_FILE_2).toFile();
        if (!pexFile.exists()) {
            throw new DDPException("Pex Data file is missing: " + pexFile);
        }
        Config pexCfg = ConfigFactory.parseFile(pexFile).resolveWith(varsCfg);

        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        if (!studyDto.getGuid().equals("cmi-pancan")) {
            throw new DDPException("This task is only for the pancan study!");
        }

        sqlHelper = handle.attach(SqlHelper.class);

        //find and delete existing OSTEO study redirect workflow transition
        List<Long> workflowTransitionIds = sqlHelper.getPancanOsteoStudyRedirectWorkflowIds();
        DBUtils.checkDelete(2, sqlHelper.deleteOsteoRedirectWorkflowTransitions(workflowTransitionIds));
        log.info("Deleted workflow transition with IDs: {}", workflowTransitionIds);

        //insert updated osteo study redirect workflow transitions
        addWorkflows(handle, studyDto);

        //update block visibility pex expressions to handle OSTEO (C_SARCOMAS_OSTEOSARCOMA) from non english REDIRECT pex
        String currentExpr = pexCfg.getString("is_not_redirect_current").trim();
        String newExpr = pexCfg.getString("is_not_redirect_new").trim();
        String searchExpr = String.format("%s%s%s", "%", currentExpr, "%").trim();
        int rowCount = sqlHelper.updatePancanOsteoBlockPex(searchExpr, currentExpr, newExpr);
        DBUtils.checkUpdate(8, rowCount);
        log.info("Updated {} rows in expression table for Expr is_not_redirect_current", rowCount);

        currentExpr = pexCfg.getString("addchild_is_not_redirect_current").trim();
        newExpr = pexCfg.getString("addchild_is_not_redirect_new").trim();
        searchExpr = String.format("%s%s%s", "%", currentExpr, "%").trim();
        rowCount = sqlHelper.updatePancanOsteoBlockPex(searchExpr, currentExpr, newExpr);
        DBUtils.checkUpdate(4, rowCount);
        log.info("Updated {} rows in expression table for Expr addchild_is_not_redirect_current", rowCount);

    }

    private void addWorkflows(Handle handle, StudyDto studyDto) {
        List<? extends Config> workflows = dataCfg.getConfigList("workflowTransitions");
        log.info("adding workflows");
        WorkflowBuilder workflowBuilder = new WorkflowBuilder(studyCfg, studyDto);
        for (Config workflowCfg : workflows) {
            workflowBuilder.insertTransitionSet(handle, workflowCfg);
            log.info("Inserted workflow transition: {}", workflowCfg);
        }
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select trans.workflow_transition_id "
                + "  from workflow_transition as trans "
                + "  join umbrella_study as s on s.umbrella_study_id = trans.umbrella_study_id "
                + "  join workflow_state as next_state on next_state.workflow_state_id = trans.next_state_id "
                + "  join workflow_state_type as next_state_type on next_state_type.workflow_state_type_id "
                + " = next_state.workflow_state_type_id "
                + " left join workflow_activity_state as next_act_state on next_act_state.workflow_state_id "
                + " = next_state.workflow_state_id "
                + " left join expression as expr on expr.expression_id = trans.precondition_expression_id "
                + " left join workflow_study_redirect_state as next_redirect_state on "
                + " next_redirect_state.workflow_state_id = next_state.workflow_state_id "
                + " where s.guid = 'cmi-pancan' "
                + " and trans.is_active "
                + " and next_state_type.workflow_state_type_code = 'STUDY_REDIRECT' "
                + " and study_guid = 'CMI-OSTEO'")
        List<Long> getPancanOsteoStudyRedirectWorkflowIds();

        @SqlUpdate("delete workflow_transition  "
                + "from workflow_transition "
                + "where workflow_transition_id in (<workflowTransitionIds>)")
        int deleteOsteoRedirectWorkflowTransitions(@BindList("workflowTransitionIds") List<Long> workflowTransitionIds);

        @SqlUpdate("update expression\n"
                + "set expression_text = REPLACE(expression_text, :currentExpr, :newExpr) "
                + "where expression_text like :searchExpr")
        int updatePancanOsteoBlockPex(@Bind("searchExpr") String searchExpr, @Bind("currentExpr") String currentExpr,
                                      @Bind("newExpr") String newExpr);

    }

}
