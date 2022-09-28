package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class MpcFollowupV2 implements CustomTask {
    private static final String DATA_FILE = "patches/followup-v2.conf";
    private static final String STUDY_GUID = "cmi-mpc";
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
    }

    @Override
    public void run(Handle handle) {
        SqlHelper helper = handle.attach(SqlHelper.class);
        List<? extends Config> expressions = dataCfg.getConfigList("expressions");
        expressions.forEach(exprConfig -> {
            String oldExpr = exprConfig.getString("oldExpr");
            String newExpr = exprConfig.getString("newExpr");
            if (helper.updateExpression(oldExpr, newExpr) == 0) {
                throw new DDPException("Old expression not found, probably patch is already applied");
            }
            log.info("Replaced {} expression with {}", oldExpr, newExpr);
        });
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update expression set expression_text = :newExpr where expression_text = :oldExpr")
        int updateExpression(@Bind("oldExpr") String oldExpr, @Bind("newExpr") String newExpr);

    }

}
