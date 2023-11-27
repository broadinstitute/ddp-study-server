package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityValidation;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;

/**
 * Task to make additional edits as part of the "Brain Tumor Project" rename.
 *
 * <p>This should be ran right after the BrainRename task. This assumes that activities will have a new version from
 * the BrainRename task, so it will make edits using that as the latest version.
 */
@Slf4j
public class BrainRenameEdits extends BrainRename {
    private static final String ACTIVITY_DATA_FILE = "patches/rename-activities.conf";

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

        this.gson = GsonUtil.standardGson();
        this.validator = new GsonPojoValidator();
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));

        this.handle = handle;
        this.activityDao = handle.attach(ActivityDao.class);
        this.activityI18nDao = handle.attach(ActivityI18nDao.class);
        this.templateDao = handle.attach(TemplateDao.class);
        this.questionDao = handle.attach(QuestionDao.class);
        this.jdbiActivity = handle.attach(JdbiActivity.class);
        this.jdbiVersion = handle.attach(JdbiActivityVersion.class);
        this.jdbiValidation = handle.attach(JdbiActivityValidation.class);
        this.jdbiSubstitution = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);

        editConsentContent(activityDataCfg.getConfig("selfConsent"));
        editConsentContent(activityDataCfg.getConfig("parentalConsent"));
        editConsentContent(activityDataCfg.getConfig("consentAssent"));
        terminateAboutYouQuestions();
        editPostConsentTitle(activityDataCfg.getConfig("postConsent"));
        editPostConsentTitle(activityDataCfg.getConfig("childPostConsent"));
        updateSelfActivityDisplayOrders();
        disableConsentEmailEvent();

        log.info("Brain Tumor Project additional edits finished");
    }

    private void editConsentContent(Config activityCfg) {
        String activityCode = activityCfg.getString("activityCode");
        String versionTag = activityCfg.getString("newVersionTag");
        log.info("Editing activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto versionDto = findActivityLatestVersion(activityId);
        FormActivityDef activity = findActivityDef(activityCode, versionDto.getVersionTag());

        // Recreate the metadata used for new version.
        RevisionMetadata baseMeta = makeActivityRevMetadata(activityCode, versionTag);
        var meta = new RevisionMetadata(versionDto.getRevStart(), baseMeta.getUserId(), baseMeta.getReason());

        Set<String> preambleVariableNames = Set.of(activityCfg.getString("preambleVariableName"));
        Set<String> spacingVariableNames = new HashSet<>(activityCfg.getStringList("spacingVariableNames"));
        Set<String> titleVariableNames = new HashSet<>(activityCfg.getStringList("titleVariableNames"));

        List<TemplateVariable> preambleVariables = new ArrayList<>();
        List<TemplateVariable> spacingVariables = new ArrayList<>();
        List<TemplateVariable> titleVariables = new ArrayList<>();

        streamActivityVariables(activity, versionDto)
                .forEach(variable -> {
                    String name = variable.getName();
                    if (preambleVariableNames.contains(name)) {
                        preambleVariables.add(variable);
                    } else if (spacingVariableNames.contains(name)) {
                        spacingVariables.add(variable);
                    } else if (titleVariableNames.contains(name)) {
                        titleVariables.add(variable);
                    }
                });

        // Bold the project name in the preamble.
        if (preambleVariables.size() != 1) {
            throw new DDPException("Expected one preamble variable but found " + preambleVariables.size());
        }
        editConsentVariables(activityCode, meta, versionDto.getRevId(),
                "preamble", preambleVariables, text -> text.replace(
                        "\"The Brain Tumor Project\"",
                        "<span class=\"bold\">\"The Brain Tumor Project\"</span>"));

        // Change <br> spacing to use <p> spacing.
        editConsentVariables(activityCode, meta, versionDto.getRevId(),
                "spacing", spacingVariables, text -> text.replace("<br>", "</p><p>"));

        // Make block title bigger by using <h3>.
        editConsentVariables(activityCode, meta, versionDto.getRevId(),
                "title", titleVariables, text -> "<h3>" + text + "</h3>");
    }

    private void editConsentVariables(String activityCode, RevisionMetadata metadata, long revisionId,
                                      String name, List<TemplateVariable> variables,
                                      Function<String, String> editor) {
        for (var variable : variables) {
            Translation sub = extractSingleTranslation(variable);
            String newText = editor.apply(sub.getText());
            long currentRevisionId = sub.getRevisionId().get();
            if (currentRevisionId == revisionId) {
                updateTranslationInPlace(sub, newText);
                log.info("Updated translation in-place for template variable: ${}", variable.getName());
            } else {
                revisionTranslation(variable, sub, metadata, newText, revisionId);
            }
        }
        log.info("Updated {} {} variables for activity {}", variables.size(), name, activityCode);
    }

    private void terminateAboutYouQuestions() {
        Config activityCfg = activityDataCfg.getConfig("aboutYou");
        String activityCode = activityCfg.getString("activityCode");
        String versionTag = activityCfg.getString("newVersionTag");
        log.info("Editing activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto versionDto = findActivityLatestVersion(activityId);
        FormActivityDef activity = findActivityDef(activityCode, versionDto.getVersionTag());

        // Recreate the metadata used for new version.
        RevisionMetadata baseMeta = makeActivityRevMetadata(activityCode, versionTag);
        var meta = new RevisionMetadata(versionDto.getRevStart(), baseMeta.getUserId(), baseMeta.getReason());

        String yearStableId = activityCfg.getString("terminateQuestions.yearStableId");
        String countryStableId = activityCfg.getString("terminateQuestions.countryStableId");
        String zipStableId = activityCfg.getString("terminateQuestions.zipStableId");

        QuestionBlockDef yearQuestionBlock = null;
        QuestionBlockDef countryQuestionBlock = null;
        QuestionBlockDef zipQuestionBlock = null;
        for (var section : activity.getAllSections()) {
            for (var block : section.getBlocks()) {
                if (block.getBlockType() == BlockType.QUESTION) {
                    var questionBlock = (QuestionBlockDef) block;
                    var question = questionBlock.getQuestion();
                    if (yearStableId.equals(question.getStableId())) {
                        yearQuestionBlock = questionBlock;
                    } else if (countryStableId.equals(question.getStableId())) {
                        countryQuestionBlock = questionBlock;
                    } else if (zipStableId.equals(question.getStableId())) {
                        zipQuestionBlock = questionBlock;
                    }
                }
            }
        }

        if (yearQuestionBlock == null) {
            throw new DDPException("Could not find question: " + yearStableId);
        } else if (countryQuestionBlock == null) {
            throw new DDPException("Could not find question: " + countryStableId);
        } else if (zipQuestionBlock == null) {
            throw new DDPException("Could not find question: " + zipStableId);
        }

        var sectionBlockDao = handle.attach(SectionBlockDao.class);

        sectionBlockDao.disableBlock(yearQuestionBlock.getBlockId(), meta);
        log.info("Disabled question block for: {}", yearStableId);

        sectionBlockDao.disableBlock(countryQuestionBlock.getBlockId(), meta);
        log.info("Disabled question block for: {}", countryStableId);

        sectionBlockDao.disableBlock(zipQuestionBlock.getBlockId(), meta);
        log.info("Disabled question block for: {}", zipStableId);
    }

    private void editPostConsentTitle(Config activityCfg) {
        String activityCode = activityCfg.getString("activityCode");
        log.info("Editing activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        String newTitle = activityCfg.getString("newTitle");

        ActivityI18nDetail i18nDetail = activityI18nDao
                .findDetailsByActivityIdAndTimestamp(activityId, Instant.now().toEpochMilli())
                .iterator().next();
        var newI18nDetail = new ActivityI18nDetail(
                i18nDetail.getId(),
                i18nDetail.getActivityId(),
                i18nDetail.getLangCodeId(),
                i18nDetail.getIsoLangCode(),
                i18nDetail.getName(),
                i18nDetail.getSecondName(),
                newTitle,
                i18nDetail.getSubtitle(),
                i18nDetail.getDescription(),
                i18nDetail.getRevisionId());
        activityI18nDao.updateDetails(List.of(newI18nDetail));
        log.info("Updated title for activity {}", activityCode);
    }

    private void updateSelfActivityDisplayOrders() {
        Config activityCfg = activityDataCfg.getConfig("aboutYou");
        String activityCode = activityCfg.getString("activityCode");
        long aboutYouActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        int displayOrder = activityCfg.getInt("newDisplayOrder");
        DBUtils.checkUpdate(1, jdbiActivity.updateDisplayOrderById(aboutYouActivityId, displayOrder));
        log.info("Updated display order for activity {} to {}", activityCode, displayOrder);

        activityCfg = activityDataCfg.getConfig("postConsent");
        activityCode = activityCfg.getString("activityCode");
        long postConsentActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        displayOrder = activityCfg.getInt("newDisplayOrder");
        DBUtils.checkUpdate(1, jdbiActivity.updateDisplayOrderById(postConsentActivityId, displayOrder));
        log.info("Updated display order for activity {} to {}", activityCode, displayOrder);
    }

    private void disableConsentEmailEvent() {
        Config activityCfg = activityDataCfg.getConfig("selfConsent");
        String activityCode = activityCfg.getString("activityCode");
        long consentActivityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        // Find the (CONSENT, CREATED -> EMAIL) event that's a duplicate of the "participant welcome" email.
        // The one we're looking for is the event without a cancel expression (those are the consent reminders).
        EventConfiguration consentCreatedEmailEvent = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId())
                .stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS)
                .filter(event -> event.getEventActionType() == EventActionType.NOTIFICATION)
                .filter(event -> StringUtils.isBlank(event.getCancelExpression()))
                .filter(event -> {
                    ActivityStatusChangeTrigger trigger = (ActivityStatusChangeTrigger) event.getEventTrigger();
                    return trigger.getStudyActivityId() == consentActivityId
                            && trigger.getInstanceStatusType() == InstanceStatusType.CREATED;
                })
                .findFirst()
                .orElseThrow(() -> new DDPException("Could not find CONSENT CREATED email event"));

        long eventId = consentCreatedEmailEvent.getEventConfigurationId();
        DBUtils.checkUpdate(1, handle.attach(JdbiEventConfiguration.class).updateIsActiveById(eventId, false));
        log.info("Disabled consent-created email event with id={}", eventId);
    }
}
