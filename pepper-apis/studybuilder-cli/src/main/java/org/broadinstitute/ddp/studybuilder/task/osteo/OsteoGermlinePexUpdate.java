package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class OsteoGermlinePexUpdate implements CustomTask {

    public static String CURR_PEX = "user.studies[\"cmi-osteo\"].forms[\"GERMLINE_CONSENT_ADDENDUM_PEDIATRIC\"].questions[\"ADDENDUM_CONSENT_BOOL_PEDIATRIC\"].answers.hasTrue()\n" +
            "                           && operator.studies[\"cmi-osteo\"].forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() >= 7";

    public static String NEW_PEX = "user.studies[\"cmi-osteo\"].forms[\"GERMLINE_CONSENT_ADDENDUM_PEDIATRIC\"].questions[\"ADDENDUM_CONSENT_BOOL_PEDIATRIC\"].answers.hasTrue()\n" +
            "                                      && ((operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance() && operator.studies[\"cmi-osteo\"].forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() >= 7)\n" +
            "                                          || (user.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance() && user.studies[\"cmi-osteo\"].forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() >= 7))";


    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        ///not used
    }

    @Override
    public void run(Handle handle) {

        updatePediatricGermlinePex(handle);
    }

    public void updatePediatricGermlinePex(Handle handle) {

        List<Long> matchedExprIds = handle.attach(OsteoGermlinePexUpdate.SqlHelper.class).getPexIdToUpd();
        log.info("Matched {} pex expressions", matchedExprIds.size());
        JdbiExpression jdbiExpression = handle.attach(JdbiExpression.class);
        int updatedPexCount = 0;
        for (Long expressionId : matchedExprIds) {
            String currentExpr = jdbiExpression.getExpressionById(expressionId);
            if (currentExpr.contains("operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].hasInstance()")) {
                continue;
            }
            int udpCount = jdbiExpression.updateById(expressionId, NEW_PEX);
            DBUtils.checkUpdate(1, udpCount);
            updatedPexCount++;
            log.info("Updated expressionId  {} with expr text \n{}. \nOld expr: \n{} ", expressionId, NEW_PEX, currentExpr);
        }
        log.info("Updated {} pex expressions", updatedPexCount);
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select e.expression_id from block b , block__expression bee, expression e " +
                "where bee.block_id = b.block_id " +
                "and e.expression_id = bee.expression_id " +
                "and e.expression_text like '%user.studies[\"CMI-OSTEO\"].forms[\"GERMLINE_CONSENT_ADDENDUM_PEDIATRIC\"].questions[\"ADDENDUM_CONSENT_BOOL_PEDIATRIC\"].answers.hasTrue()%"
                + " && operator.studies[\"CMI-OSTEO\"].forms[\"PREQUAL\"].questions[\"CHILD_CURRENT_AGE\"].answers.value() >= 7%'")
        List<Long> getPexIdToUpd();

    }

}

