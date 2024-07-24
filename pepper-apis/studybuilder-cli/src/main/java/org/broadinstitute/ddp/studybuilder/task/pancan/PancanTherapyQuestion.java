package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiCompositeQuestion;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

@Slf4j
public class PancanTherapyQuestion implements CustomTask {
    private static final String DATA_FILE = "patches/composite-therapy-question.conf";

    private Config dataCfg;
    private String studyGuid;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        studyGuid = dataCfg.getString("studyGuid");
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }
        gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

        SqlHelper helper = handle.attach(SqlHelper.class);
        QuestionDao questionDao = handle.attach(QuestionDao.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        JdbiCompositeQuestion jdbiCompositeQuestion = handle.attach(JdbiCompositeQuestion.class);
        JdbiFormSectionBlock jdbiFormSectionBlock = handle.attach(JdbiFormSectionBlock.class);
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);

        log.info("updating current treatment question...");
        String currCompositeQuestionStableId = dataCfg.getString("currCompositeQuestionStableId");
        String newCompositeStableId = dataCfg.getString("newQuestionStableId");

        QuestionDto currCompositeQuestionDto = jdbiQuestion
                .findLatestDtoByStudyIdAndQuestionStableId(studyDto.getId(), currCompositeQuestionStableId)
                .orElseThrow(() -> new DDPException("Could not find question " + currCompositeQuestionStableId));
        QuestionDto currPLQuestionDto = jdbiQuestion
                .findLatestDtoByStudyIdAndQuestionStableId(studyDto.getId(), "THERAPY_NAME_CHOOSE")
                .orElseThrow(() -> new DDPException("Could not find question " + "THERAPY_NAME_CHOOSE"));

        long currCompositeBlockId = helper.findQuestionBlockId(currCompositeQuestionDto.getId());
        SectionBlockMembershipDto currSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(currCompositeBlockId).get();
        //update existing child questions display order to accommodate new txt question
        int rowCount = helper.incrementCompositeChildrenDisplayOrder(currCompositeQuestionDto.getId());
        DBUtils.checkUpdate(2, rowCount);
        //insert new txt question for current medication/therapies
        TextQuestionDef textQuestionDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("textQuestion")), TextQuestionDef.class);
        questionDao.insertQuestion(currCompositeQuestionDto.getActivityId(), textQuestionDef, currCompositeQuestionDto.getRevisionId());
        //add as child
        jdbiCompositeQuestion.insertChildren(currCompositeQuestionDto.getId(), Arrays.asList(textQuestionDef.getQuestionId()), Arrays.asList(0));
        log.info("added new txt treatment question : {} as child to composite question...", textQuestionDef.getQuestionId());

        //populate new composite for past medications/therapies
        log.info("creating new composite question...");
        QuestionBlockDef questionBlockDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("current-therapy-composite")), QuestionBlockDef.class);
        sectionBlockDao.insertBlockForSection(currCompositeQuestionDto.getActivityId(), currSectionDto.getSectionId(),
                65, questionBlockDef, currCompositeQuestionDto.getRevisionId());
        log.info("inserted new composite question for past treatments : {} ", questionBlockDef.getQuestion().getQuestionId());

        //update stableIds
        helper.updateCompositeQuestionStableId(currCompositeQuestionDto.getId(), newCompositeStableId);
        helper.updateCompositeQuestionStableId(currPLQuestionDto.getId(), "CURRENT_MED_CLINICAL_TRIAL");

    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update composite_question__question cqq "
                + "set display_order = display_order + 1 "
                + "where cqq.parent_question_id = :parentQuestionId")
        int incrementCompositeChildrenDisplayOrder(@Bind("parentQuestionId") long parentQuestionId);

        @SqlUpdate("update question_stable_code qsc "
                + "set qsc.stable_id = :stableId "
                + "where qsc.question_stable_code_id = (select question_stable_code_id from question where question_id = :questionId)")
        int updateCompositeQuestionStableId(@Bind("questionId") long questionId, @Bind("stableId") String stableId);

        @SqlQuery("select block_id from block__question where question_id = :questionId")
        int findQuestionBlockId(@Bind("questionId") long questionId);

    }

}
