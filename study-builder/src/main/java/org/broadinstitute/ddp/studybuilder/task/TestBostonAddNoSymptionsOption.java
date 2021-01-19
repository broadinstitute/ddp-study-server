package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestBostonAddNoSymptionsOption implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(BrainPrequalV2.class);
    private static final String DATA_FILE = "patches/adhoc-no-symptom.conf";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;
    private Config dataCfg;
    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        String studyGuid = "testboston";
        String activityCode = "ADHOC_SYMPTOM";
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid("testboston");

        ActivityDao activityDao = handle.attach(ActivityDao.class);

        ActivityVersionDto currentActivityVerDto = activityDao.getJdbiActivityVersion().findByActivityCodeAndVersionTag(
                studyDto.getId(), activityCode, "v1").get();


        // find the adhoc symptom activity
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .orElseThrow(() -> new DDPException("Could not find id for activity " + activityCode + " and study id " + studyGuid));

        ActivityDef currActivityDef = activityDao.findDefByDtoAndVersion(activityDto, currentActivityVerDto);
        FormActivityDef currFormActivityDef = (FormActivityDef)currActivityDef;

        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);

        // find the symptoms question
        QuestionDto symptomsQuestionDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyDto.getId(), "ADHOC_SYMPTOMS").get();

        List<FormSectionDef> bodySections = currFormActivityDef.getSections();
        FormSectionDef plQuestionSec = bodySections.get(0);
        FormBlockDef formBlockDef = plQuestionSec.getBlocks().get(0);

        // load the new option config
        Config option = dataCfg.getConfig("option");

        var newOption = new Gson().fromJson(ConfigUtil.toJson(option), PicklistOptionDef.class);

        ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
                .getActiveVersion(currFormActivityDef.getActivityId())
                .orElseThrow(() -> new DDPException("Could not find latest version for activity " + activityCode));

        long revisionId = versionDto.getRevId();

         formBlockDef.getQuestions().forEach(questionDef -> {
            // add the option to the question
            if (questionDef.getStableId().equalsIgnoreCase(symptomsQuestionDto.getStableId())) {
                PicklistQuestionDef plQuestionDef = (PicklistQuestionDef) questionDef;
                plQuestionDao.insertOption(questionDef.getQuestionId(),newOption,plQuestionDef.getAllPicklistOptions().size() + 1,revisionId);
            }
        });

    }
}
