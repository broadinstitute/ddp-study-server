package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.CopyConfigurationDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.CopyAnswerEventAction;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Task to fix copy event for Post Consent due to new sex/gender questions.
 */
@Slf4j
public class BrainRenameFixPostConsentCopy implements CustomTask {
    private static final String ACTIVITY_DATA_FILE = "patches/rename-activities.conf";
    private static final String GENDER_DATA_FILE = "patches/rename-gender-questions.conf";

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Config activityDataCfg;
    private Config genderDataCfg;

    private Handle handle;
    private StudyDto studyDto;
    private UserDto adminUser;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.activityDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        file = cfgPath.getParent().resolve(GENDER_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.genderDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));
        this.handle = handle;

        fixChildPostConsentGenderPicklistOptionStableIds();
        fixPostConsentCopyEvent();
    }

    private void fixChildPostConsentGenderPicklistOptionStableIds() {
        Config activityCfg = activityDataCfg.getConfig("childPostConsent");
        String activityCode = activityCfg.getString("activityCode");
        log.info("Editing activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto versionDto = findActivityLatestVersion(activityId);
        FormActivityDef activity = findActivityDef(activityCode, versionDto.getVersionTag());

        String childGenderStableId = genderDataCfg.getString("childGenderIdentity.stableId");
        PicklistQuestionDef childGenderQuestion = null;
        for (var section : activity.getAllSections()) {
            for (var block : section.getBlocks()) {
                if (block.getBlockType() == BlockType.QUESTION) {
                    var questionBlock = (QuestionBlockDef) block;
                    var question = questionBlock.getQuestion();
                    if (childGenderStableId.equals(question.getStableId())) {
                        childGenderQuestion = (PicklistQuestionDef) question;
                        break;
                    }
                }
            }
        }
        if (childGenderQuestion == null) {
            throw new DDPException("Could not find question " + childGenderStableId);
        }

        // Picklist option stable ids need to match up in order for answer-copying to work.
        // So we update the question's picklist options to match up with the adult Post Consent.
        var mappings = Map.of(
                "BOY", "MAN",
                "GIRL", "WOMAN",
                "TRANSGENDER_GIRL", "TRANSGENDER_WOMAN",
                "TRANSGENDER_BOY", "TRANSGENDER_MAN");
        var helper = handle.attach(SqlHelper.class);
        for (var option : childGenderQuestion.getAllPicklistOptions()) {
            String newStableId = mappings.get(option.getStableId());
            if (newStableId != null) {
                DBUtils.checkUpdate(1, helper.updatePicklistOptionStableId(option.getOptionId(), newStableId));
                log.info("Updated picklist question {} option {} to new stable id {}",
                        childGenderStableId, option.getStableId(), newStableId);
            }
        }

        log.info("Finished updating picklist option stable ids for activity {}", activityCode);
    }

    private void fixPostConsentCopyEvent() {
        Config activityCfg = activityDataCfg.getConfig("postConsent");
        String activityCode = activityCfg.getString("activityCode");
        log.info("Working on copy event for {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        long copyConfigId = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId())
                .stream()
                .filter(event -> event.getEventActionType().equals(EventActionType.COPY_ANSWER))
                .filter(event -> {
                    if (event.getEventTriggerType().equals(EventTriggerType.ACTIVITY_STATUS)) {
                        var trigger = (ActivityStatusChangeTrigger) event.getEventTrigger();
                        return trigger.getStudyActivityId() == activityId
                                && trigger.getInstanceStatusType() == InstanceStatusType.CREATED;
                    }
                    return false;
                })
                .map(event -> ((CopyAnswerEventAction) event.getEventAction()).getCopyConfigurationId())
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find copy event for activity " + activityCode));
        log.info("Found copy event with copy configuration id " + copyConfigId);

        var copyConfigDao = handle.attach(CopyConfigurationDao.class);
        CopyConfiguration currentConfig = copyConfigDao
                .findCopyConfigById(copyConfigId)
                .orElseThrow(() -> new DDPException("Could not find copy configuration with id " + copyConfigId));

        var eventBuilder = new EventBuilder(studyCfg, studyDto, adminUser.getUserId());
        List<Config> copyConfigPairs = List.copyOf(genderDataCfg.getConfigList("copyConfigPairs"));
        CopyConfiguration newCopyPairs = eventBuilder.buildCopyConfiguration(studyDto.getId(), false, List.of(), copyConfigPairs);

        int executionOrder = currentConfig.getPairs().size();
        for (var newPair : newCopyPairs.getPairs()) {
            executionOrder++;
            newPair.setOrder(executionOrder);
            long id = copyConfigDao.addCopyPairToConfig(studyDto.getId(), copyConfigId, newPair);
            log.info("Added copy pair with id={} to copy configuration {}", id, copyConfigId);
        }

        log.info("Finished updating copy event for {}", activityCode);
    }

    private ActivityVersionDto findActivityLatestVersion(long activityId) {
        return handle.attach(JdbiActivityVersion.class)
                .getActiveVersion(activityId)
                .orElseThrow(() -> new DDPException("Could not find active version for activity " + activityId));
    }

    private FormActivityDef findActivityDef(String activityCode, String versionTag) {
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyIdAndCode(studyDto.getId(), activityCode).get();
        ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, versionTag).get();
        return (FormActivityDef) handle.attach(ActivityDao.class)
                .findDefByDtoAndVersion(activityDto, versionDto);
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update picklist_option set picklist_option_stable_id = :stableId where picklist_option_id = :optionId")
        int updatePicklistOptionStableId(@Bind("optionId") long optionId, @Bind("stableId") String newStableId);
    }
}
