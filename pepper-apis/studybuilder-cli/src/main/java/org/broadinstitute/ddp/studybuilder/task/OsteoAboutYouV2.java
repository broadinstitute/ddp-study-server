package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.*;
import org.broadinstitute.ddp.db.dto.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

public class OsteoAboutYouV2 implements CustomTask{

    private static final Logger LOG = LoggerFactory.getLogger(MBCAboutYouV2.class);
    private static final String DATA_FILE = "patches/about-you-v2.conf";
    private static final String CMI_OSTEO = "CMI-OSTEO";

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

        if (!studyCfg.getString("study.guid").equals(CMI_OSTEO)) {
            throw new DDPException("This task is only for the " + CMI_OSTEO + " study!");
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

        //disable DIAGNOSES_DATE question
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        JdbiFormSectionBlock jdbiFormSectionBlock = handle.attach(JdbiFormSectionBlock.class);
        QuestionDao questionDao = handle.attach(QuestionDao.class);

        QuestionDto currRaceDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "DIAGNOSIS_DATE").get();
        long currRaceBlockId = helper.findQuestionBlockId(currRaceDto.getId());
        SectionBlockMembershipDto currRaceSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(currRaceBlockId).get();
        long terminatedRevId = jdbiRevision.copyAndTerminate(currRaceSectionDto.getRevisionId(), meta);
        helper.updateFormSectionBlockRevision(currRaceSectionDto.getId(), terminatedRevId);

        //disable SYMPTOMS_START_TIME question
        QuestionDto currHispanicDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "SYMPTOMS_START_TIME").get();
        long currHispanicBlockId = helper.findQuestionBlockId(currHispanicDto.getId());
        questionDao.disablePicklistQuestion(currHispanicDto.getId(), meta);
        SectionBlockMembershipDto currHispanicSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(currHispanicBlockId).get();
        helper.updateFormSectionBlockRevision(currHispanicSectionDto.getId(), terminatedRevId);

        //disable INITIAL_BODY_LOC question
        QuestionDto initialLoc = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "INITIAL_BODY_LOC").get();
        long initBodyLocBlockId = helper.findQuestionBlockId(initialLoc.getId());
        questionDao.disablePicklistQuestion(initialLoc.getId(), meta);
        SectionBlockMembershipDto initBodyLocSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(initBodyLocBlockId).get();
        helper.updateFormSectionBlockRevision(initBodyLocSectionDto.getId(), terminatedRevId);

        //disable CURRENT_BODY_LOC question
        QuestionDto currnetLoc = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "CURRENT_BODY_LOC").get();
        long currBodyLocBlockId = helper.findQuestionBlockId(currnetLoc.getId());
        questionDao.disablePicklistQuestion(currnetLoc.getId(), meta);
        SectionBlockMembershipDto currBodyLocSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(currBodyLocBlockId).get();
        helper.updateFormSectionBlockRevision(currBodyLocSectionDto.getId(), terminatedRevId);

        //disable HAD_RADIATION question
        QuestionDto hadRadiationDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "HAD_RADIATION").get();
        long hadRadiationBlockId = helper.findQuestionBlockId(hadRadiationDto.getId());
        questionDao.disablePicklistQuestion(hadRadiationDto.getId(), meta);
        SectionBlockMembershipDto hadRadiationSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(hadRadiationBlockId).get();
        helper.updateFormSectionBlockRevision(hadRadiationSectionDto.getId(), terminatedRevId);

        //disable THERAPIES_RECEIVED question
        QuestionDto therapiesReceivedDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "THERAPIES_RECEIVED").get();
        long therapiesReceivedBlockId = helper.findQuestionBlockId(therapiesReceivedDto.getId());
        questionDao.disablePicklistQuestion(therapiesReceivedDto.getId(), meta);
        SectionBlockMembershipDto therapiesReceivedSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(therapiesReceivedBlockId).get();
        helper.updateFormSectionBlockRevision(therapiesReceivedSectionDto.getId(), terminatedRevId);


        //disable EVER_RELAPSED question
        QuestionDto everRelapsedDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "EVER_RELAPSED").get();
        long everRelapsedBlockId = helper.findQuestionBlockId(everRelapsedDto.getId());
        questionDao.disablePicklistQuestion(everRelapsedDto.getId(), meta);
        SectionBlockMembershipDto everRelapsedSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(everRelapsedBlockId).get();
        helper.updateFormSectionBlockRevision(everRelapsedSectionDto.getId(), terminatedRevId);

        //disable CURRENTLY_TREATED question
        QuestionDto currTreatedDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "CURRENTLY_TREATED").get();
        long currTreatedBlockId = helper.findQuestionBlockId(currTreatedDto.getId());
        questionDao.disablePicklistQuestion(currTreatedDto.getId(), meta);
        SectionBlockMembershipDto currTreatedSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(currTreatedBlockId).get();
        helper.updateFormSectionBlockRevision(currTreatedSectionDto.getId(), terminatedRevId);


        //disable OTHER_CANCERS question
        QuestionDto otherCancersDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "OTHER_CANCERS").get();
        long otherCancersBlockId = helper.findQuestionBlockId(otherCancersDto.getId());
        questionDao.disablePicklistQuestion(otherCancersDto.getId(), meta);
        SectionBlockMembershipDto otherCancersSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(otherCancersBlockId).get();
        helper.updateFormSectionBlockRevision(otherCancersSectionDto.getId(), terminatedRevId);

        //disable HISPANIC question
        QuestionDto hispanicDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "HISPANIC").get();
        long hispanicBlockId = helper.findQuestionBlockId(hispanicDto.getId());
        questionDao.disablePicklistQuestion(hispanicDto.getId(), meta);
        SectionBlockMembershipDto hispanicSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(hispanicBlockId).get();
        helper.updateFormSectionBlockRevision(hispanicSectionDto.getId(), terminatedRevId);

        //disable RACE question
        QuestionDto raceDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "RACE").get();
        long raceBlockId = helper.findQuestionBlockId(raceDto.getId());
        questionDao.disablePicklistQuestion(raceDto.getId(), meta);
        SectionBlockMembershipDto raceSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(raceBlockId).get();
        helper.updateFormSectionBlockRevision(raceSectionDto.getId(), terminatedRevId);

        //disable HOW_HEAR question
        QuestionDto howHearDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "HOW_HEAR").get();
        long howHearBlockId = helper.findQuestionBlockId(howHearDto.getId());
        questionDao.disablePicklistQuestion(howHearDto.getId(), meta);
        SectionBlockMembershipDto howHearSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(howHearBlockId).get();
        helper.updateFormSectionBlockRevision(howHearSectionDto.getId(), terminatedRevId);

        dataCfg.getConfigList("sections").forEach(e->{
            SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
            FormBlockDef raceDef = gson.fromJson(ConfigUtil.toJson(e), FormBlockDef.class);
            long newV2RevId = activityVersionDto.getRevId();
            sectionBlockDao.insertBlockForSection(activityId, currRaceSectionDto.getSectionId(),
            currRaceSectionDto.getDisplayOrder(), raceDef, newV2RevId);
        });

//        //add new RACE (self) question
//        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
//        FormBlockDef raceDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("raceQuestion")), FormBlockDef.class);
//        long newV2RevId = activityVersionDto.getRevId();
//        sectionBlockDao.insertBlockForSection(activityId, currRaceSectionDto.getSectionId(),
//                currRaceSectionDto.getDisplayOrder(), raceDef, newV2RevId);
//
//        //add new GENDER_IDENTITY question
//        FormBlockDef genderDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("genderIdentityQuestion")), FormBlockDef.class);
//        sectionBlockDao.insertBlockForSection(activityId, currRaceSectionDto.getSectionId(),
//                currRaceSectionDto.getDisplayOrder() + 2, genderDef, newV2RevId);
//
//        //add new ASSIGNED_SEX question
//        FormBlockDef assignedSexDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("assignedSexQuestion")), FormBlockDef.class);
//        sectionBlockDao.insertBlockForSection(activityId, currRaceSectionDto.getSectionId(),
//                currRaceSectionDto.getDisplayOrder() + 4, assignedSexDef, newV2RevId);
//
//        //Add new citations block to closing
//        FormBlockDef citationsDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("citationsBlock")), FormBlockDef.class);
//        ActivityDef currActivityDef = activityDao.findDefByDtoAndVersion(activityDto, activityVersionDto);
//        FormActivityDef formActivityDef = (FormActivityDef) currActivityDef;
//        FormSectionDef closingSectionDef =  formActivityDef.getClosing();
//        sectionBlockDao.insertBlockForSection(activityId, closingSectionDef.getSectionId(),
//                (closingSectionDef.getBlocks().size() * 10) + 10, citationsDef, newV2RevId);
//        LOG.info("Added citations");
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select block_id from block__question where question_id = :questionId")
        int findQuestionBlockId(@Bind("questionId") long questionId);

        @SqlUpdate("update form_section__block set revision_id = :revisionId where form_section__block_id = :formSectionBlockId")
        int updateFormSectionBlockRevision(@Bind("formSectionBlockId") long formSectionBlockId, @Bind("revisionId") long revisionId);
    }

}
