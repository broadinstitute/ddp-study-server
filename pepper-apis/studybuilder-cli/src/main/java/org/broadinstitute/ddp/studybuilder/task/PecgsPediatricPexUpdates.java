package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class PecgsPediatricPexUpdates implements CustomTask {

    public static String AGE_7_GT = "> 7";
    public static String AGE_7_GTE = ">= 7";


    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        ///not used
    }

    @Override
    public void run(Handle handle) {

        updatePediatricAgePex(handle);
    }

    public void updatePediatricAgePex(Handle handle) {

        List<Long> matchedExprIds = handle.attach(PecgsPediatricPexUpdates.SqlHelper.class).getAge7Pex();
        log.info("Matched {} pex expressions" , matchedExprIds.size());
        JdbiExpression jdbiExpression = handle.attach(JdbiExpression.class);
        int updatedPexCount = 0;
        for (Long expressionId : matchedExprIds) {
            String currentExpr = jdbiExpression.getExpressionById(expressionId);
            String updatedExpr = currentExpr.replace(AGE_7_GT,  AGE_7_GTE);
            int udpCount = jdbiExpression.updateById(expressionId, updatedExpr);
            DBUtils.checkUpdate(1, udpCount);
            updatedPexCount++;
            log.info("Updated expressionId  {} with expr text {}. Old expr: {} ", expressionId, updatedExpr, currentExpr);
        }
        log.info("Updated {} pex expressions" , updatedPexCount);
    }


    private interface SqlHelper extends SqlObject {
        @SqlQuery("select expression_id from expression where expression_text like '%> 7%' and "
                + " (expression_text like '%cmi-lms%' OR expression_text like '%CMI-OSTEO%')")
        List<Long> getAge7Pex();
    }

}

