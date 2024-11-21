package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;

@Slf4j
public class PancanLmsRedirectUpdate implements CustomTask {

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

        //update pex expressions to include the two Leiomyosarcoma cancers
        //("C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA", "C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA") to ADD_CHILD LMS redirects
        int rowCount = sqlHelper.updatePancanLmsRedirectPex();
        log.info("Updated {} rows in expression table", rowCount);
    }


    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update expression  set expression_text = REPLACE(expression_text, \n"
                + "'children[\"PRIMARY_CANCER_ADD_CHILD\"].answers.hasOptionStartsWith(\"C_SARCOMAS_S_LEIOMYO\")', \n"
                + "'children[\"PRIMARY_CANCER_ADD_CHILD\"].answers.hasAnyOption(\"C_SARCOMAS_S_LEIOMYO_LMS_SARCOMA\", "
                + " \"C_GYNECOLOGIC_UTERINE_LEIOMYOSARCOMA\", \"C_SARCOMA_CUTANEOUS_LEIMYOSARCOMA\")')  \n"
                + "where expression_text like '%children[\"PRIMARY_CANCER_ADD_CHILD\"]."
                + "answers.hasOptionStartsWith(\"C_SARCOMAS_S_LEIOMYO\")%' ")
        int updatePancanLmsRedirectPex();

    }


}
