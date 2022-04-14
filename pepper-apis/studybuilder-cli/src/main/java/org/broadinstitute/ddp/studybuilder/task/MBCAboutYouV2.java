package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Slf4j
public class MBCAboutYouV2 implements CustomTask {
    private static final String DATA_FILE = "patches/aboutyou-version2.conf";
    private static final String MBC = "cmi-mbc";

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
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        if (!studyCfg.getString("study.guid").equals(MBC)) {
            throw new DDPException("This task is only for the " + MBC + " study!");
        }

        this.studyCfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now(); //parse(dataCfg.getString("timestamp"));
        gson = GsonUtil.standardGson();

    }

    @Override
    public void run(Handle handle) {
        //creates version: 2 for AboutYou activity.
        //Add updated new RACE question and disable existing RACE and HISPANIC questions
        //Add GENDER and SEX questions
        //Add Citations

        LanguageStore.init(handle);
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        var activityDao = handle.attach(ActivityDao.class);
        String activityCode = dataCfg.getString("activityCode");
        String studyGuid = studyDto.getGuid();
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .orElseThrow(() -> new DDPException(
                        "Could not find activity for activity code " + activityCode + " and study id " + studyGuid));
        long studyId = studyDto.getId();
        long activityId = activityDto.getActivityId();
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
        long currRaceBlockId = helper.findQuestionBlockId(currRaceDto.getId());
        SectionBlockMembershipDto currRaceSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(currRaceBlockId).get();
        long terminatedRevId = jdbiRevision.copyAndTerminate(currRaceSectionDto.getRevisionId(), meta);
        helper.updateFormSectionBlockRevision(currRaceSectionDto.getId(), terminatedRevId);

        //disable HISPANIC question
        QuestionDto currHispanicDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "HISPANIC").get();
        long currHispanicBlockId = helper.findQuestionBlockId(currHispanicDto.getId());
        questionDao.disablePicklistQuestion(currHispanicDto.getId(), meta);
        SectionBlockMembershipDto currHispanicSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(currHispanicBlockId).get();
        helper.updateFormSectionBlockRevision(currHispanicSectionDto.getId(), terminatedRevId);

        //add new RACE (self) question
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        FormBlockDef raceDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("raceQuestion")), FormBlockDef.class);
        long newV2RevId = activityVersionDto.getRevId();
        sectionBlockDao.insertBlockForSection(activityId, currRaceSectionDto.getSectionId(),
                currRaceSectionDto.getDisplayOrder(), raceDef, newV2RevId);

        //add new GENDER_IDENTITY question
        FormBlockDef genderDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("genderIdentityQuestion")), FormBlockDef.class);
        sectionBlockDao.insertBlockForSection(activityId, currRaceSectionDto.getSectionId(),
                currRaceSectionDto.getDisplayOrder() + 2, genderDef, newV2RevId);

        //add new ASSIGNED_SEX question
        FormBlockDef assignedSexDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("assignedSexQuestion")), FormBlockDef.class);
        sectionBlockDao.insertBlockForSection(activityId, currRaceSectionDto.getSectionId(),
                currRaceSectionDto.getDisplayOrder() + 4, assignedSexDef, newV2RevId);

        //Add new citations block to closing
        FormBlockDef citationsDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("citationsBlock")), FormBlockDef.class);
        ActivityDef currActivityDef = activityDao.findDefByDtoAndVersion(activityDto, activityVersionDto);
        FormActivityDef formActivityDef = (FormActivityDef) currActivityDef;
        FormSectionDef closingSectionDef =  formActivityDef.getClosing();
        sectionBlockDao.insertBlockForSection(activityId, closingSectionDef.getSectionId(),
                (closingSectionDef.getBlocks().size() * 10) + 10, citationsDef, newV2RevId);
        log.info("Added citations");
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select block_id from block__question where question_id = :questionId")
        int findQuestionBlockId(@Bind("questionId") long questionId);

        @SqlUpdate("update form_section__block set revision_id = :revisionId where form_section__block_id = :formSectionBlockId")
        int updateFormSectionBlockRevision(@Bind("formSectionBlockId") long formSectionBlockId, @Bind("revisionId") long revisionId);
    }

}
