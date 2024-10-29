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
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;

@Slf4j
public class PancanOsteoRedirectUrlUpdate implements CustomTask {

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

        //update OSTEO REDIRECT URLS
        String newURL = varsCfg.getString("osteoRedirectUrl").trim();
        String currentURL = newURL.replace("home", "count-me-in");
        DBUtils.checkUpdate(1, sqlHelper.updateRedirectUrl(currentURL, newURL));
        log.info("Updated Osteo EN URL to {}", newURL);

        newURL = varsCfg.getString("osteoRedirectUrlES").trim();
        currentURL = newURL.replace("home", "count-me-in");
        DBUtils.checkUpdate(1, sqlHelper.updateRedirectUrl(currentURL, newURL));
        log.info("Updated Osteo ES URL to {}", newURL);

    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update workflow_study_redirect_state "
                + "set redirect_url = :newUrl , study_name = 'Osteosarcoma Project'"
                + "where redirect_url = :currentUrl")
        int updateRedirectUrl(@Bind("currentUrl") String currentUrl, @Bind("newUrl") String newUrl);
    }

}
