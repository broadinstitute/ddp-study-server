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
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class PancanTrccRedirects implements CustomTask {

    private static final String DATA_FILE = "patches/trcc-redirect-workflows.conf";
    private static final String DATA_FILE_2 = "patches/trcc-redirect-block-pex.conf";
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

        //insert updated trcc study redirect workflow transitions
        addWorkflows(handle, studyDto);
        log.info("Added trcc redirect workflow transitions");

        //update block visibility pex expressions to handle TRCC REDIRECT pex
        String currentExpr = pexCfg.getString("is_not_redirect_current").trim();
        String newExpr = pexCfg.getString("is_not_redirect_new").trim();
        String searchExpr = String.format("%s%s%s", "%", currentExpr, "%").trim();
        log.info("Current Expr: {}", currentExpr);
        log.info("Next EXPR: {}", currentExpr);
        log.info("Search EXPR: {}", currentExpr);

        int rowCount = sqlHelper.updatePancanTrccBlockPex(searchExpr, currentExpr, newExpr);
        DBUtils.checkUpdate(14, rowCount);
        log.info("Updated {} rows in expression table for Expr is_not_redirect_current", rowCount);

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

        @SqlUpdate("update expression\n"
                + "set expression_text = REPLACE(expression_text, :currentExpr, :newExpr) "
                + "where expression_text like :searchExpr")
        int updatePancanTrccBlockPex(@Bind("searchExpr") String searchExpr, @Bind("currentExpr") String currentExpr,
                                     @Bind("newExpr") String newExpr);

    }

}
