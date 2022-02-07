package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrainPostConsentV2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(BrainPostConsentV2.class);
    private static final String DATA_FILE = "patches/postconsent-version2.conf";
    private static final String BRAIN = "cmi-brain";

    private Config studyCfg;
    private Config dataCfg;
    private Instant timestamp;
    private String versionTag;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);

        if (!studyCfg.getString("study.guid").equals(BRAIN)) {
            throw new DDPException("This task is only for the " + BRAIN + " study!");
        }

        this.studyCfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now(); //parse(dataCfg.getString("timestamp"));
        gson = GsonUtil.standardGson();

    }

    @Override
    public void run(Handle handle) {
        //creates version: 2 for POSTCONSENT (self/adult post-consent) activity.

        LanguageStore.init(handle);
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        var activityDao = handle.attach(ActivityDao.class);
        String activityCode = dataCfg.getString("activityCode");
        String studyGuid = studyDto.getGuid();
        long studyId = studyDto.getId();
        long activityId = ActivityBuilder.findActivityId(handle, studyId, activityCode);
        SqlHelper helper = handle.attach(SqlHelper.class);
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyGuid, activityCode, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), reason);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);

        //change version
        ActivityVersionDto activityVersionDto = activityDao.changeVersion(activityId, versionTag, meta);

        //disable RACE question
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        JdbiFormSectionBlock jdbiFormSectionBlock = handle.attach(JdbiFormSectionBlock.class);
        QuestionDao questionDao = handle.attach(QuestionDao.class);

        QuestionDto currRaceDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "RACE").get();
        long terminatedRevId = jdbiRevision.copyAndTerminate(currRaceDto.getRevisionId(), meta);
        long currRaceBlockId = helper.findQuestionBlockId(currRaceDto.getId());
        SectionBlockMembershipDto currRaceSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(currRaceBlockId).get();
        helper.updateFormSectionBlockRevision(currRaceBlockId, terminatedRevId);

        //disable HISPANIC question
        QuestionDto currHispanicDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "HISPANIC").get();
        long currHispanicBlockId = helper.findQuestionBlockId(currHispanicDto.getId());
        questionDao.disablePicklistQuestion(currHispanicDto.getId(), meta);
        helper.updateFormSectionBlockRevision(currHispanicBlockId, terminatedRevId);

        //add new RACE (self) question
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        FormBlockDef raceDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("raceQuestion")), FormBlockDef.class);
        long newV2RevId = jdbiRevision.insertStart(timestamp.toEpochMilli(), adminUser.getId(), "postconsent version#2 RACE");
        sectionBlockDao.insertBlockForSection(activityId, currRaceSectionDto.getSectionId(),
                currRaceSectionDto.getDisplayOrder(), raceDef, newV2RevId);
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select block_id from block__question where question_id = :questionId")
        int findQuestionBlockId(@Bind("questionId") long questionId);

        @SqlUpdate("update form_section__block set revision_id = :revisionId where block_id = :blockId")
        int updateFormSectionBlockRevision(@Bind("blockId") long blockId, @Bind("revisionId") long revisionId);
    }

}
