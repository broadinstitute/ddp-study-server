package org.broadinstitute.ddp.studybuilder.task;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.FormSectionMembershipDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;

@Slf4j
public class SingularMedicalReleaseV2 implements CustomTask {
    private static final String DATA_FILE = "patches/medical-record-release_v2.conf";
    private static final String STUDY_GUID = "singular";

    private Config studyCfg;
    private Config dataCfg;
    private Instant timestamp;
    private String versionTag;
    private Gson gson;
    private ActivityDao activityDao;
    private JdbiActivity jdbiActivity;
    private JdbiActivityVersion jdbiActVersion;
    private JdbiFormActivityFormSection jdbiFormActivityFormSection;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }

        this.studyCfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
        gson = GsonUtil.standardGson();

    }

    // This patch will create a new version of medical release for Singular
    // Approach here is that will read from file a new and complete activity definition from file
    // We will then create a new version of this activity.
    // Important: to avoid clashing with existing question stable ids, in new file any question that was mean to
    // be reused from original activity was named with _NEW prefix.
    // Once old activity version has been updated so it is deprecated and new activity version has been stored
    // we will update the new activity version to replace questions with stable id that ends with _NEW with original
    // question.

    @Override
    public void run(Handle handle) {
        activityDao = handle.attach(ActivityDao.class);
        jdbiActVersion = handle.attach(JdbiActivityVersion.class);
        jdbiActivity = handle.attach(JdbiActivity.class);
        jdbiFormActivityFormSection = handle.attach(JdbiFormActivityFormSection.class);

        LanguageStore.init(handle);

        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));


        String studyGuid = studyDto.getGuid();
        long studyId = studyDto.getId();

        String activityCode = dataCfg.getString("activityCode");
        ActivityDto activityDto = jdbiActivity
                .findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .orElseThrow(() -> new DDPException(
                        "Could not find activity for activity code " + activityCode + " and study id " + studyGuid));
        long activityId = activityDto.getActivityId();

        ActivityVersionDto originalV1VersionDto = jdbiActVersion.findByActivityCodeAndVersionTag(studyDto.getId(),
                activityCode, "v1").orElseThrow(DDPException::new);

        ActivityVersionDto v2VersionDto = createNewVersion(activityId, activityCode, studyGuid, adminUser);

        ActivityVersionDto updatedV1VersionDto = jdbiActVersion.findByActivityCodeAndVersionTag(studyDto.getId(),
                activityCode, "v1").orElseThrow(DDPException::new);

        // save before update for checking
        List<FormSectionMembershipDto> v1SectionMembershipsBeforeVersionUpdate = jdbiFormActivityFormSection
                .findOrderedSectionMemberships(activityId, originalV1VersionDto.getRevStart());

        updateSectionVersion(activityId, updatedV1VersionDto);

        List<FormSectionMembershipDto> v1SectionMembersAfterVersionUpdate = jdbiFormActivityFormSection
                .findOrderedSectionMemberships(activityId, updatedV1VersionDto.getRevStart());
        // Does section list look same before and after version update?
        if (!extractSectionIds(v1SectionMembershipsBeforeVersionUpdate)
                .equals(extractSectionIds(v1SectionMembersAfterVersionUpdate))) {
            throw new DDPException("We messed up updating sections");
        }


        // Reading the new activity def. note that it is a complete activity! Not just a chunk!
        FormActivityDef v2ActivityDefFromHocon = gson.fromJson(ConfigUtil.toJson(dataCfg), FormActivityDef.class);

        // We are just interested in having a new revision of the contents; rest remains same
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        sectionBlockDao.insertBodySections(activityId, v2ActivityDefFromHocon.getSections(), v2VersionDto.getRevId());

        // checking we can read v2 back?
        FormActivityDef activityDefV2 = findActivityDef(studyId, activityCode, "v2", handle);

        // If we did it right, we should have a list of section ids that is different from v1
        if (extractSectionIds(activityDefV2).equals(extractSectionIds(v1SectionMembersAfterVersionUpdate))) {
            throw new DDPException("Sections do not appear to have been updated");
        }

        FormActivityDef activityDefV1 = findActivityDef(studyId, activityCode, "v1", handle);

        // And V1 section ids should still look the same
        if (!extractSectionIds(activityDefV1).equals(extractSectionIds(v1SectionMembersAfterVersionUpdate))) {
            throw new DDPException("V1 sections are messed after adding v2 sections");
        }

        int originalV1QuestionCount = activityDefV1.getAllQuestions().size();
        if (originalV1QuestionCount < 5) {
            throw new DDPException("Oops. We should have a bunch of question in v1!");
        }

        long originalV1QuestionWithSuffix = activityDefV1.getAllQuestions().stream()
                .filter(q -> q.getStableId().endsWith("_NEW")).count();
        if (originalV1QuestionWithSuffix > 0) {
            throw new DDPException("Oops! We had questions with suffix of _NEW already. This is not going to work");
        }

        // find the questions in v2 with _NEW suffix and replace them with corresponding one in v1
        activityDefV2.getAllQuestions().stream()
                .filter(q -> q.getStableId().endsWith("_NEW"))
                .map(q -> new String[]{q.getStableId().substring(0, q.getStableId().length() - "_NEW".length()), q.getStableId()})
                .forEach(toUseToDelete ->
                        replaceQuestionInNewActivityVersion(activityCode, studyId, versionTag,
                                "v1", toUseToDelete[1], toUseToDelete[0], handle));


        // checking work. Any questions in v2 that are not fixed?
        long countOfQuestionsNotFixed = findActivityDef(studyId, activityCode, "v2", handle)
                .getAllQuestions().stream().filter(q -> q.getStableId().endsWith("_NEW"))
                .count();

        if (countOfQuestionsNotFixed != 0L) {
            throw new DDPException("We did not fix all the questions");
        }


    }

    private void replaceQuestionInNewActivityVersion(String activityCode, long studyId, String newVersionTag,
                                                     String oldVersionTag, String questionStableIdToRemove,
                                                     String questionStableIdToUse, Handle handle) {
        FormActivityDef activityDefV2 = findActivityDef(studyId, activityCode, newVersionTag, handle);
        QuestionBlockDef v2QuestionBlockToUpdate = activityDefV2.getSections().stream()
                .flatMap(section -> section.getBlocks().stream())
                .filter(block -> block.getBlockType() == BlockType.QUESTION)
                .map(block -> (QuestionBlockDef) block)
                .filter(block -> block.getQuestion().getStableId().equals(questionStableIdToRemove))
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find block for stable id:" + questionStableIdToRemove));

        QuestionDef questionToReplaceAndDelete = v2QuestionBlockToUpdate.getQuestion();

        FormActivityDef activityDefV1 = findActivityDef(studyId, activityCode, oldVersionTag, handle);

        QuestionDef questionToAttachToActivityV2 = activityDefV1.getQuestionByStableId(questionStableIdToUse);

        assignQuestionToBlock(v2QuestionBlockToUpdate, questionToAttachToActivityV2, handle);

        // The original question from v2 is not needed anymore. Let's do some cleanup
        QuestionDao questionDao = handle.attach(QuestionDao.class);
        questionDao.deleteQuestion(questionToReplaceAndDelete);

        // We are checking our work now
        Long originalQuestionId = findActivityDef(studyId, activityCode, oldVersionTag, handle)
                .getQuestionByStableId(questionStableIdToUse).getQuestionId();
        FormActivityDef activityDefV2AfterQuestionUpdate = findActivityDef(studyId, activityCode, "v2", handle);
        Long questionIdInV2 = activityDefV2AfterQuestionUpdate.getQuestionByStableId(questionStableIdToUse).getQuestionId();
        if (!originalQuestionId.equals(questionIdInV2)) {
            throw new DDPException("Update of question did not work");
        }
        QuestionDef questionThatShouldHaveBeenDeleted = activityDefV2AfterQuestionUpdate.getQuestionByStableId(questionStableIdToRemove);
        if (questionThatShouldHaveBeenDeleted != null) {
            throw new DDPException("New activity still hanging on to question that should have been removed");
        }
    }

    private int assignQuestionToBlock(QuestionBlockDef questionBlockToUpdate, QuestionDef questionToPreserve, Handle handle) {
        return handle.execute("update block__question set question_id=? where block_id=?",
                questionToPreserve.getQuestionId(), questionBlockToUpdate.getBlockId());
    }

    private FormActivityDef findActivityDef(long studyId, String activityCode, String versionTag, Handle handle) {
        ActivityVersionDto originalV1VersionDto = handle.attach(JdbiActivityVersion.class)
                .findByActivityCodeAndVersionTag(studyId, activityCode, versionTag)
                .orElseThrow(() -> new DDPException("Could not find activity version"));
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        ActivityDto activityDto = jdbiActivity.findActivityByStudyIdAndCode(studyId, activityCode)
                .orElseThrow(() -> new DDPException("Could not find activity with study id: " + studyId + " and code:"
                        + activityCode));
        var def = handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, originalV1VersionDto);
        if (def instanceof FormActivityDef) {
            return (FormActivityDef) def;
        } else {
            throw new DDPException("FormActivityDef with code:" + activityCode + " " + versionTag + " not found");
        }
    }

    private ActivityVersionDto createNewVersion(long activityId, String activityCode, String studyGuid, User adminUser) {
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyGuid, activityCode, versionTag);

        RevisionMetadata metadataForV1Revision = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), reason);

        //change version. the versiondto returned is for new version
        return activityDao.changeVersion(activityId, versionTag, metadataForV1Revision);
    }

    private List<Long> extractSectionIds(List<FormSectionMembershipDto> sectionMembers) {
        return sectionMembers.stream().map(FormSectionMembershipDto::getSectionId).collect(toList());
    }

    private List<Long> extractSectionIds(FormActivityDef activityDef) {
        return activityDef.getSections().stream().map(FormSectionDef::getSectionId).collect(toList());
    }

    private void updateSectionVersion(long activityId, ActivityVersionDto v1Dto) {
        List<FormSectionMembershipDto> v1SectionMemberships = jdbiFormActivityFormSection
                .findOrderedSectionMemberships(activityId, v1Dto.getRevStart());
        log.info("Before updating");
        v1SectionMemberships.forEach(each -> log.info("sectionId:" + each.getSectionId() + ",revisionId:" + each.getRevisionId()));

        jdbiFormActivityFormSection.bulkUpdateRevisionIdsByIds(v1SectionMemberships.stream()
                .map(m -> m.getId())
                .collect(toList()), Collections.nCopies(v1SectionMemberships.size(), v1Dto.getRevId()));
        List<FormSectionMembershipDto> v1SectionMembershipsAfter = jdbiFormActivityFormSection
                .findOrderedSectionMemberships(activityId, v1Dto.getRevStart());
        v1SectionMembershipsAfter.forEach(each -> log.info("sectionId:" + each.getSectionId() + ",revisionId:" + each.getRevisionId()));
        log.info("updated revision of {}", StringUtils.join(v1SectionMemberships.stream()
                .map(m -> m.getId()).collect(toList())));
    }

}
