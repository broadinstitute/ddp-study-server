package org.broadinstitute.ddp.studybuilder.task.atcp;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class ATCPInsertPlaceHolder implements CustomTask {

    private static final String DATA_FILE = "patches/dob-place-holders.conf";
    private static final String ATCP_STUDY = "ATCP";

    private static final Gson gson = GsonUtil.standardGson();

    private Config dataCfg;
    private Config dataCfg2;
    private Config varsCfg;
    private Path cfgPath;
    private String versionTag;
    private SqlHelper sqlHelper;
    private SectionBlockDao sectionBlockDao;
    private JdbiVariableSubstitution jdbiVarSubst;
    private JdbiRevision jdbiRevision;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;

        String studyGuid = studyCfg.getString("study.guid");
        if (!studyGuid.equalsIgnoreCase(ATCP_STUDY)) {
            throw new DDPException("This task is only for the " + ATCP_STUDY + " study!");
        }
        versionTag = dataCfg.getString("versionTag");
    }

    @Override
    public void run(Handle handle) {

        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));
        this.sqlHelper = handle.attach(SqlHelper.class);
        this.sectionBlockDao = handle.attach(SectionBlockDao.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);

        JdbiActivityVersion jdbiActivityVersion = handle.attach(JdbiActivityVersion.class);
        ActivityVersionDto registrationVersionDto = jdbiActivityVersion.findByActivityCodeAndVersionTag(
                studyDto.getId(), "REGISTRATION", "v1").get();
        ActivityVersionDto consentVersionDto = jdbiActivityVersion.findByActivityCodeAndVersionTag(
                studyDto.getId(), "CONSENT", versionTag).get();
        ActivityVersionDto consentEditVersionDto = jdbiActivityVersion.findByActivityCodeAndVersionTag(
                studyDto.getId(), "CONSENT_EDIT", versionTag).get();
        ActivityVersionDto assentVersionDto = jdbiActivityVersion.findByActivityCodeAndVersionTag(
                studyDto.getId(), "ASSENT", versionTag).get();

        insertDateOfBirthPlaceHolder(handle, registrationVersionDto, "registration-dob");

        insertDateOfBirthPlaceHolder(handle, consentVersionDto, "self-consent-dob");
        insertDateOfBirthPlaceHolder(handle, consentVersionDto, "self-consent-guardian-dob");

        insertDateOfBirthPlaceHolder(handle, consentEditVersionDto, "self-consent-edit-dob");
        insertDateOfBirthPlaceHolder(handle, consentVersionDto, "self-consent-edit-guardian-dob");

        insertDateOfBirthPlaceHolder(handle, assentVersionDto, "assent-dob");
        insertDateOfBirthPlaceHolder(handle, assentVersionDto, "assent-date");
    }

    private void insertDateOfBirthPlaceHolder(Handle handle, ActivityVersionDto versionDto, String elementName) {
        //load activity conf file and look for the question
        //load question from DB
        //insert the placeHolder template

        Config config = dataCfg.getConfig(elementName);
        QuestionBlockDef questionBlockDef = gson.fromJson(ConfigUtil.toJson(config), QuestionBlockDef.class);

        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        QuestionDto questionDto = jdbiQuestion.findLatestDtoByStudyGuidAndQuestionStableId(
                ATCP_STUDY, questionBlockDef.getQuestion().getStableId()).get();

        DateQuestionDef dateQuestionDef = (DateQuestionDef) questionBlockDef.getQuestion();
        TemplateDao templateDao = handle.attach(TemplateDao.class);
        long placeHolderTemplateId = templateDao.insertTemplate(dateQuestionDef.getPlaceholderTemplate(), versionDto.getRevId());
        int numUpdated = sqlHelper.updatePlaceholderTemplateIdByQuestionId(questionDto.getId(), placeHolderTemplateId);
        if (numUpdated != 1) {
            throw new DDPException(String.format(
                    "Expected to update 1 date question with questionId=%d but updated %d", questionDto.getId(), numUpdated));
        }

        log.info("Inserted new placeHolder template : {} for question: {}", placeHolderTemplateId, questionDto.getStableId());
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update date_question set placeholder_template_id = :placeholderId where question_id = :questionId")
        int updatePlaceholderTemplateIdByQuestionId(@Bind("questionId") long questionId,
                                                    @Bind("placeholderId") Long placeholderTemplateId);

    }

}
