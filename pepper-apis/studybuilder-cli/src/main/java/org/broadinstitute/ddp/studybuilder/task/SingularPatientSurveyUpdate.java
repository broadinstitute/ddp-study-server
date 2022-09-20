package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;

/**
 * Task to add new picklist option to a singular question.
 */
@Slf4j
public class SingularPatientSurveyUpdate implements CustomTask {

    private static final String ACTIVITY_DATA_FILE = "patches/patient-survey-had-arrhythmia-new-opt.conf";

    private Config studyCfg;
    private Config dataCfg;
    private Gson gson;

    private StudyDto studyDto;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        this.gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK:: SingularPatientSurveyUpdate ");
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        insertNewOption(handle);
    }

    private void insertNewOption(Handle handle) {

        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);

        PicklistOptionDef optionDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("option")), PicklistOptionDef.class);
        QuestionDto question = findLatestQuestionDto(handle, dataCfg.getString("questionSid"));
        String sidBeforeOption = dataCfg.getString("sidBeforeOption");

        var groupAndOptionDtos = plQuestionDao.findOrderedGroupAndOptionDtos(question.getId(), question.getRevisionStart());

        int order = groupAndOptionDtos.getUngroupedOptions()
                .stream()
                .filter(opt -> opt.getStableId().equals(sidBeforeOption))
                .findFirst()
                .orElseThrow(() ->
                        new DDPException("Couldn't find the option that should be before new option with StableId " + sidBeforeOption))
                .getDisplayOrder() + 1;

        plQuestionDao.insertOption(question.getId(), optionDef, order, question.getRevisionId());

        log.info("Successfully added option {} with order {}  for question {}",
                optionDef.getStableId(), order, dataCfg.getString("questionSid"));
    }

    private QuestionDto findLatestQuestionDto(Handle handle, String questionSid) {
        var jdbiQuestion = handle.attach(JdbiQuestion.class);
        return jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyDto.getId(), questionSid)
                .orElseThrow(() -> new DDPException("Couldnt find question with stable code " + questionSid));
    }
}
