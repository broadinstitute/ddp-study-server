package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

public class OsteoPrequalUpdate implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(OsteoPrequalUpdate.class);
    private static final String FILE = "patches/prequal-updates.conf";
    private static final String DATA_FILE = "prequal.conf";
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String ACTIVITY_CODE = "PREQUAL";

    private Config dataCfg;
    private Config studyCfg;
    private Config cfg;
    private Gson gson;
    private Instant timestamp;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        File file = cfgPath.getParent().resolve(FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        File filecfg = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!filecfg.exists()) {
            throw new DDPException("Data file is missing: " + filecfg);
        }
        this.cfg = ConfigFactory.parseFile(filecfg).resolveWith(varsCfg);
        this.studyCfg = studyCfg;
        this.gson = GsonUtil.standardGson();
        this.timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        long activityId = ActivityBuilder.findActivityId(handle, adminUser.getId(), ACTIVITY_CODE);
        String versionTag = dataCfg.getString("versionTag");

        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                STUDY_GUID, ACTIVITY_CODE, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), reason);
        revisionPrequal(activityId, dataCfg, handle, meta, versionTag);
    }

    private void revisionPrequal(long activityId, Config dataCfg, Handle handle, RevisionMetadata meta, String versionTag) {
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, meta);
        insertBlock(handle, dataCfg, activityId, version2, meta);
        updateQuestion(handle, activityId, dataCfg, meta);
    }

    private void insertBlock(Handle handle, Config dataCfg, long activityId, ActivityVersionDto def, RevisionMetadata revisionMetadata) {
        Config blockConfig = dataCfg.getConfig("blocksInsert").getConfig("block");
        int order = dataCfg.getConfig("blocksInsert").getInt("blockOrder");
        int sectionOrder = dataCfg.getConfig("blocksInsert").getInt("sectionOrder");
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY_GUID, ACTIVITY_CODE).get();
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, def);
        FormSectionDef currentSectionDef = currentDef.getSections().get(sectionOrder);
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(blockConfig), FormBlockDef.class);

        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        RevisionDto revDto = RevisionDto.fromStartMetadata(def.getRevId(), revisionMetadata);
        sectionBlockDao.addBlock(activityId, currentSectionDef.getSectionId(),
                order, blockDef, revDto);
    }

    private void updateQuestion(Handle handle, long activityId, Config dataCfg, RevisionMetadata revisionMetadata) {
        String stableId = dataCfg.getConfig("questionUpdate").getString("stableId");
        Config questionConf = dataCfg.getConfig("questionUpdate").getConfig("question");
        PicklistQuestionDef questionBlockDef = gson.fromJson(ConfigUtil.toJson(questionConf), PicklistQuestionDef.class);
        Template promptTemplate = questionBlockDef.getPromptTemplate();
        JdbiQuestion questionContent = handle.attach(JdbiQuestion.class);
        QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                .findDtoByActivityIdAndQuestionStableId(activityId, stableId).get();
        TemplateDao templateDao = handle.attach(TemplateDao.class);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newRevId = jdbiRevision.copyAndTerminate(questionDto.getRevisionId(), revisionMetadata);
        int numUpdated = questionContent.updateRevisionIdById(questionDto.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DDPException(String.format(
                    "Unable to terminate active question with id=%d, revId=%d, promptTemplateId=%d",
                    questionDto.getId(), questionDto.getRevisionId(), questionDto.getPromptTemplateId()));
        }

        templateDao.disableTemplate(questionDto.getPromptTemplateId(), revisionMetadata);
        RevisionDto revisionDto = RevisionDto.fromStartMetadata(questionDto.getRevisionId(), revisionMetadata);
        templateDao.insertTemplate(promptTemplate, revisionDto.getId());
    }
}
