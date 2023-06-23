package org.broadinstitute.ddp.studybuilder.task.rgp;

import java.io.File;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

@Slf4j
public class RgpStateOptionUpdate implements CustomTask {

    private static final String STUDY_GUID = "RGP";
    private static final String DATA_FILE = "patches/state-picklist-puerto-rico.conf";
    private static final int DISPLAY_ORDER = 415; // between PA (display_order=410) and RI (display_order=420)

    private Gson gson;
    private Config studyCfg;
    private Config varsCfg;
    private Config dataCfg;

    private StudyDto studyDto;
    private UserDto adminUser;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.gson = GsonUtil.standardGson();
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        SqlHelper helper = handle.attach(SqlHelper.class);

        Long questionId = helper.findStatePLQuestionIdsByStudyGuid(STUDY_GUID);
        log.info("About to add new option 'Puerto Rico' into STATE picklist question: question_id {} in study {} ", questionId, STUDY_GUID);

        Config option = dataCfg.getConfig("statePR");
        PicklistOptionDef pickListOptionDef = gson.fromJson(ConfigUtil.toJson(option), PicklistOptionDef.class);

        long activityId = helper.findActivityIdByQuestionId(questionId);
        ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
                .getActiveVersion(activityId)
                .orElseThrow(() -> new DDPException("Could not find latest version for activity " + activityId));

        long revisionId = versionDto.getRevId();

        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);
        long optionId = plQuestionDao.insertOption(questionId, pickListOptionDef, DISPLAY_ORDER, revisionId);
        log.info("Inserted new picklist option 'Puerto Rico' with option id {}", optionId);
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select distinct q.question_id "
                + "from question_stable_code qsc , question q, picklist_question plq, umbrella_study s "
                + "where q.question_stable_code_id = qsc.question_stable_code_id "
                + "and s.umbrella_study_id = qsc.umbrella_study_id  "
                + "and plq.question_id = q.question_id "
                + "and qsc.stable_id = 'STATE' "
                + "and s.guid = :studyGuid")
        Long findStatePLQuestionIdsByStudyGuid(@Bind("studyGuid") String studyGuid);


        @SqlQuery("select q.study_activity_id from question q where question_id = :questionId")
        long findActivityIdByQuestionId(@Bind("questionId") long questionId);
    }
}
