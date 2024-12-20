package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;

@Slf4j
public class Osteo2AboutYouV2PexUpdate implements CustomTask {
    private static final String STUDY_GUID = "CMI-OSTEO";

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
    }

    @Override
    public void run(Handle handle) {
        SqlHelper helper = handle.attach(SqlHelper.class);
        long expressionId = helper.getAboutYouWhoFillExpressionId();
        DBUtils.checkUpdate(1, helper.updateExpression(expressionId, "!user.studies[\"CMI-OSTEO\"].isGovernedParticipant()"));
        log.info("Updated expression ID ");
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update expression set expression_text = :newExpr where expression_id = :expressionId")
        int updateExpression(@Bind("expressionId") long expressionId, @Bind("newExpr") String newExpr);

        @SqlQuery("SELECT be.expression_id " 
                + "FROM umbrella_study s, study_activity sa, question q , "
                + "question_stable_code qsc , block__question bq, block__expression be "
                + "where s.umbrella_study_id = sa.study_id " 
                + "and q.study_activity_id = sa.study_activity_id " 
                + "and qsc.question_stable_code_id = q.question_stable_code_id " 
                + "and bq.question_id = q.question_id " 
                + "and be.block_id = bq.block_id " 
                + "and s.guid = 'CMI-OSTEO' " 
                + "and sa.study_activity_code = 'ABOUTYOU' "
                + "and qsc.stable_id = 'WHO_IS_FILLING_ABOUTYOU' ")
        long getAboutYouWhoFillExpressionId();

    }

}
