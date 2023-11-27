package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

@Slf4j
public class AddStateOption implements CustomTask {
    private static final String[] studyGuids = {"cmi-pancan", "CMI-OSTEO", "cmi-brain", "rarex"};
    private static final int DISPLAY_ORDER = 75; //between DE and FL
    private String optionGson = "{\"allowDetails\":false,\"detailLabelTemplate\":null,\"exclusive\":false,"
            + "\"optionLabelTemplate\":{\"templateCode\":null,\"templateText\":\"$STATE_dc\",\"templateType\":\"TEXT\",\"variables\":"
            + "[{\"name\":\"STATE_dc\",\"translations\":[{\"language\":\"en\",\"text\":\"District of Columbia\"}]}]},\"stableId\":\"DC\"}";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {

        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);

        SqlHelper helper = handle.attach(SqlHelper.class);
        for (String studyGuid : studyGuids) {
            List<Long> questionIds = helper.findStatePLQuestionIdsByStudyGuid(studyGuid);
            log.info("Adding option for {} questions of study: {} ", questionIds.size(), studyGuid);
            for (Long questionId : questionIds) {
                var newOption = new Gson().fromJson(optionGson, PicklistOptionDef.class);
                log.info("Adding New PL option DC to Question: {} ", questionId);
                Long activityId = helper.findActivityIdByQuestionId(questionId);
                ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
                        .getActiveVersion(activityId)
                        .orElseThrow(() -> new DDPException("Could not find latest version for activity " + activityId));
                long revisionId = versionDto.getRevId();
                Long optionId = insertOption(questionId, newOption, DISPLAY_ORDER, revisionId, plQuestionDao);
                log.info("Inserted DC PL option : {} for question : {} ", optionId, questionId);
            }
        }
    }

    private long insertOption(long questionId, PicklistOptionDef option, int displayOrder, long revisionId,
                              PicklistQuestionDao plQuestionDao) {
        long optionId = plQuestionDao.insertOption(questionId, option, displayOrder, revisionId);
        return optionId;
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select distinct q.question_id "
                + "from question_stable_code qsc , question q, picklist_question plq, umbrella_study s "
                + "where q.question_stable_code_id = qsc.question_stable_code_id "
                + "and s.umbrella_study_id = qsc.umbrella_study_id  "
                + "and plq.question_id = q.question_id "
                + "and qsc.stable_id in ('STATE',  'SELF_STATE', 'CHILD_STATE', 'PARTICIPANT_STATE') "
                + "and s.guid = :studyGuid")
        List<Long> findStatePLQuestionIdsByStudyGuid(@Bind("studyGuid") String studyGuid);


        @SqlQuery("select q.study_activity_id from question q where question_id = :questionId")
        long findActivityIdByQuestionId(@Bind("questionId") long questionId);
    }

}
