package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityValidation;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiSendgridConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityValidationDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DecimalQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.EquationQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.AnnouncementEventAction;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.jdbi.v3.core.Handle;

/**
 * Task to rename Brain study to "Brain Tumor Project".
 *
 * <p>Requirement is to rename the project name and to reword mentions of "brain cancer" to "brain tumor" where it
 * makes sense. Changes to activities should be done via a new revision, so existing users see what they saw, and new
 * users will see the "brain tumor" word change.
 *
 * <p>New PDF configurations need to be added for the new consent revisions. Also, we're bundling in changes to the two
 * "gender" questions in the Post Consent activities.
 *
 * <p>Make sure to run Brain study's other patches (e.g. pediatric changes) before running this. See the
 * `patch-log.conf` file for what those patches are.
 */
@Slf4j
public class BrainRename implements CustomTask {
    private static final String RENAME_DATA_FILE = "patches/rename.conf";
    private static final String ACTIVITY_DATA_FILE = "patches/rename-activities.conf";
    private static final String GENDER_DATA_FILE = "patches/rename-gender-questions.conf";
    private static final String PDF_DATA_FILE = "patches/rename-pdfs.conf";
    private static final String ARROW = "->";

    protected Path cfgPath;
    protected Config studyCfg;
    protected Config varsCfg;
    protected Config renameDataCfg;
    protected Config activityDataCfg;
    protected Config genderDataCfg;
    protected Config pdfDataCfg;
    protected Gson gson;
    protected GsonPojoValidator validator;

    // Task should be ran once, so we set some shared objects here.
    protected Handle handle;
    protected ActivityDao activityDao;
    protected ActivityI18nDao activityI18nDao;
    protected TemplateDao templateDao;
    protected QuestionDao questionDao;
    protected JdbiActivity jdbiActivity;
    protected JdbiActivityVersion jdbiVersion;
    protected JdbiActivityValidation jdbiValidation;
    protected JdbiVariableSubstitution jdbiSubstitution;
    protected JdbiRevision jdbiRevision;
    protected StudyDto studyDto;
    protected UserDto adminUser;

    private static String replaceNameAndEmail(String text) {
        return text.replace("Brain Cancer Project", "Brain Tumor Project")
                .replace("info@braincancerproject.org", "info@braintumorproject.org");
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(RENAME_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.renameDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.activityDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        file = cfgPath.getParent().resolve(GENDER_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.genderDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        file = cfgPath.getParent().resolve(PDF_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.pdfDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

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

        renameProjectContactInfo();
        renameAnnouncementEventMessages();

        revisionActivity(activityDataCfg.getConfig("prequal"));
        revisionActivity(activityDataCfg.getConfig("selfConsent"));
        revisionActivity(activityDataCfg.getConfig("parentalConsent"));
        revisionActivity(activityDataCfg.getConfig("consentAssent"));
        revisionActivity(activityDataCfg.getConfig("selfRelease"));
        revisionActivity(activityDataCfg.getConfig("releaseMinor"));
        revisionActivity(activityDataCfg.getConfig("aboutYou"));
        revisionActivity(activityDataCfg.getConfig("aboutChild"));

        revisionPostConsentAndGenderQuestions("postConsent",
                "genderStableId", "transgenderStableId",
                "assignedSex", "genderIdentity");
        revisionPostConsentAndGenderQuestions("childPostConsent",
                "childGenderStableId", "childTransgenderStableId",
                "childAssignedSex", "childGenderIdentity");

        var pdfBuilder = new PdfBuilder(cfgPath.getParent(), studyCfg, studyDto, adminUser.getUserId());
        pdfBuilder.insertPdfConfig(handle, pdfDataCfg.getConfig("selfConsentPdf"));
        pdfBuilder.insertPdfConfig(handle, pdfDataCfg.getConfig("parentalConsentPdf"));
        pdfBuilder.insertPdfConfig(handle, pdfDataCfg.getConfig("consentAssentPdf"));
        pdfBuilder.insertPdfConfig(handle, pdfDataCfg.getConfig("releaseSelfConsentPdf"));
        pdfBuilder.insertPdfConfig(handle, pdfDataCfg.getConfig("releaseParentalConsentPdf"));
        pdfBuilder.insertPdfConfig(handle, pdfDataCfg.getConfig("releaseConsentAssentPdf"));

        log.info("Brain Tumor Project rename finished");
    }

    private void renameProjectContactInfo() {
        log.info("Updating study contact information...");
        DBUtils.checkUpdate(1, handle.attach(JdbiUmbrellaStudy.class)
                .updateEmailAndWebUrl(studyDto.getId(),
                        studyCfg.getString("study.studyEmail"),
                        studyCfg.getString("study.baseWebUrl")));

        log.info("Updating client password redirect url...");
        DBUtils.checkUpdate(1, handle.attach(JdbiClient.class)
                .updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
                        studyCfg.getString("client.passwordRedirectUrl"),
                        studyCfg.getString("client.id"),
                        studyCfg.getString("tenant.domain")));

        log.info("Updating sendgrid configuration...");
        DBUtils.checkUpdate(1, handle.attach(JdbiSendgridConfiguration.class)
                .updateFromDetails(studyDto.getId(),
                        studyCfg.getString("sendgrid.fromName"),
                        studyCfg.getString("sendgrid.fromEmail")));
    }

    private void renameAnnouncementEventMessages() {
        Set<Long> messageTemplateIds = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId())
                .stream()
                .filter(event -> event.getEventActionType().equals(EventActionType.ANNOUNCEMENT))
                .map(event -> ((AnnouncementEventAction) event.getEventAction()).getMessageTemplateId())
                .collect(Collectors.toSet());
        log.info("Found {} announcement event message templates", messageTemplateIds.size());

        List<Translation> translations = streamTemplateVariables(messageTemplateIds, Instant.now().toEpochMilli())
                .flatMap(variable -> variable.getTranslations().stream())
                .collect(Collectors.toList());
        log.info("Found {} announcement event template variable translations to update in-place", translations.size());

        List<Edit> edits = parseEdits(renameDataCfg, "announcementEdits");
        for (var sub : translations) {
            String newText = replaceNameAndEmail(sub.getText());
            for (var edit : edits) {
                newText = edit.apply(newText);
            }
            updateTranslationInPlace(sub, newText);
        }

        log.info("Finished updating announcement message templates");
    }

    private void revisionPostConsentAndGenderQuestions(String activityKey,
                                                       String genderStableIdKey, String transgenderStableIdKey,
                                                       String assignedSexKey, String genderIdentityKey) {
        var result = revisionActivity(activityDataCfg.getConfig(activityKey));
        var activity = result.getActivity();
        var newRevId = result.getNewVersionDto().getRevId();

        String genderStableId = genderDataCfg.getString(genderStableIdKey);
        String transgenderStableId = genderDataCfg.getString(transgenderStableIdKey);
        PicklistQuestionDef assignedSexQuestion = (PicklistQuestionDef) parseQuestionDef(
                genderDataCfg.getConfig(assignedSexKey));
        PicklistQuestionDef genderIdentityQuestion = (PicklistQuestionDef) parseQuestionDef(
                genderDataCfg.getConfig(genderIdentityKey));

        QuestionBlockDef genderQuestionBlock = null;
        QuestionBlockDef transgenderQuestionBlock = null;
        for (var section : activity.getAllSections()) {
            for (var block : section.getBlocks()) {
                if (block.getBlockType() == BlockType.QUESTION) {
                    var questionBlock = (QuestionBlockDef) block;
                    var question = questionBlock.getQuestion();
                    if (genderStableId.equals(question.getStableId())) {
                        genderQuestionBlock = questionBlock;
                    } else if (transgenderStableId.equals(question.getStableId())) {
                        transgenderQuestionBlock = questionBlock;
                    }
                }
            }
        }
        if (genderQuestionBlock == null || transgenderQuestionBlock == null) {
            throw new DDPException("Could not find old gender or transgender question blocks");
        }

        // Swap in new question by disabling old question, inserting new question,
        // and then putting new question in the same block position.
        long blockId = genderQuestionBlock.getBlockId();
        long questionId = genderQuestionBlock.getQuestion().getQuestionId();
        questionDao.disablePicklistQuestion(questionId, result.getMetadata());
        questionDao.insertQuestion(activity.getActivityId(), assignedSexQuestion, newRevId);
        questionDao.getJdbiBlockQuestion().insert(blockId, assignedSexQuestion.getQuestionId());
        log.info("Disabled question {} and swapped in question {}", genderStableId, assignedSexQuestion.getStableId());

        blockId = transgenderQuestionBlock.getBlockId();
        questionId = transgenderQuestionBlock.getQuestion().getQuestionId();
        questionDao.disablePicklistQuestion(questionId, result.getMetadata());
        questionDao.insertQuestion(activity.getActivityId(), genderIdentityQuestion, newRevId);
        questionDao.getJdbiBlockQuestion().insert(blockId, genderIdentityQuestion.getQuestionId());
        log.info("Disabled question {} and swapped in question {}", transgenderStableId, genderIdentityQuestion.getStableId());
    }

    private RevisionResult revisionActivity(Config activityCfg) {
        String activityCode = activityCfg.getString("activityCode");
        String versionTag = activityCfg.getString("newVersionTag");
        RevisionMetadata meta = makeActivityRevMetadata(activityCode, versionTag);
        log.info("Working on activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto currentVersionDto = findActivityLatestVersion(activityId);
        FormActivityDef activity = findActivityDef(activityCode, currentVersionDto.getVersionTag());

        ActivityVersionDto newVersionDto = activityDao.changeVersion(activityId, versionTag, meta);
        log.info("Created new revision {} of activity {}", newVersionDto.getVersionTag(), activityCode);

        if (activityCfg.getBoolean("editSubtitle")) {
            updateActivitySubtitleInPlace(activityId, activityCode);
        }
        if (activityCfg.getBoolean("editStatusSummaries")) {
            List<Edit> edits = parseEdits(activityCfg, "statusSummaryEdits");
            updateActivityStatusSummariesInPlace(activityId, activityCode, edits);
        }
        if (activityCfg.getBoolean("editReadOnlyHint")) {
            updateActivityReadOnlyHintInPlace(activity);
        }
        if (activityCfg.getBoolean("editActivityValidations")) {
            List<Edit> edits = parseEdits(activityCfg, "activityValidationEdits");
            updateActivityValidationsInPlace(activityId, activityCode, currentVersionDto, edits);
        }

        Map<String, VariableEdit> varEdits = parseVariableEdits(activityCfg, "variableEdits");
        Set<String> expectedVariables = varEdits.keySet();
        AtomicInteger count = new AtomicInteger(0);

        streamActivityVariables(activity, currentVersionDto)
                .filter(variable -> expectedVariables.contains(variable.getName()))
                .forEach(variable -> {
                    Translation sub = extractSingleTranslation(variable);
                    VariableEdit varEdit = varEdits.get(variable.getName());
                    String newText = varEdit.apply(sub.getText());
                    revisionTranslation(variable, sub, meta, newText, newVersionDto.getRevId());
                    count.getAndIncrement();
                });

        log.info("Renamed {} variable translations for activity {}", count.get(), activityCode);

        return new RevisionResult(activity, currentVersionDto, newVersionDto, meta);
    }

    protected void revisionTranslation(TemplateVariable variable, Translation translation,
                                     RevisionMetadata meta, String newText, long newRevisionId) {
        // Terminate the current translation text.
        long translationId = translation.getId().get();
        long currentRevisionId = translation.getRevisionId().get();
        long terminatedRevisionId = jdbiRevision.copyAndTerminate(currentRevisionId, meta);
        long[] newRevId = new long[] {terminatedRevisionId};
        int[] updated = jdbiSubstitution.bulkUpdateRevisionIdsBySubIds(List.of(translationId), newRevId);
        DBUtils.checkUpdate(1, Arrays.stream(updated).sum());

        // Add new version of translation text.
        long variableId = variable.getId().get();
        jdbiSubstitution.insert(translation.getLanguageCode(), newText, newRevisionId, variableId);
        log.info("Revisioned translation for template variable: ${}", variable.getName());
    }

    protected RevisionMetadata makeActivityRevMetadata(String activityCode, String newVersionTag) {
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, newVersionTag);
        return RevisionMetadata.now(adminUser.getUserId(), reason);
    }

    protected ActivityVersionDto findActivityLatestVersion(long activityId) {
        return jdbiVersion.getActiveVersion(activityId)
                .orElseThrow(() -> new DDPException("Could not find active version for activity " + activityId));
    }

    protected FormActivityDef findActivityDef(String activityCode, String versionTag) {
        ActivityDto activityDto = jdbiActivity
                .findActivityByStudyIdAndCode(studyDto.getId(), activityCode).get();
        ActivityVersionDto versionDto = jdbiVersion
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, versionTag).get();
        return (FormActivityDef) activityDao
                .findDefByDtoAndVersion(activityDto, versionDto);
    }

    private List<TemplateVariable> findActivityValidationVariables(long activityId, long timestamp) {
        List<Long> validationMessageIds = jdbiValidation._findByActivityId(activityId)
                .stream()
                .map(ActivityValidationDto::getErrorMessageTemplateId)
                .collect(Collectors.toList());
        return templateDao
                .loadTemplatesByIdsAndTimestamp(validationMessageIds, timestamp)
                .flatMap(template -> template.getVariables().stream())
                .collect(Collectors.toList());
    }

    protected Translation extractSingleTranslation(TemplateVariable variable) {
        if (variable.getTranslations().size() != 1) {
            throw new DDPException("Variable should only have one translation: $" + variable.getName());
        }
        return variable.getTranslations().get(0);
    }

    protected void updateTranslationInPlace(Translation sub, String newText) {
        long substitutionId = sub.getId().get();
        long currentRevisionId = sub.getRevisionId().get();
        boolean updated = jdbiSubstitution.update(substitutionId, currentRevisionId, sub.getLanguageCode(), newText);
        if (!updated) {
            throw new DDPException("Failed to update translation with id " + substitutionId);
        }
    }

    private void updateActivitySubtitleInPlace(long activityId, String activityCode) {
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
                i18nDetail.getTitle(),
                replaceNameAndEmail(i18nDetail.getSubtitle()),
                i18nDetail.getDescription(),
                i18nDetail.getRevisionId());
        activityI18nDao.updateDetails(List.of(newI18nDetail));
        log.info("Updated subtitle for activity {}", activityCode);
    }

    private void updateActivityStatusSummariesInPlace(long activityId, String activityCode, List<Edit> edits) {
        List<SummaryTranslation> oldSummaries = activityI18nDao.findSummariesByActivityId(activityId);
        List<SummaryTranslation> newSummaries = new ArrayList<>();
        for (var summary : oldSummaries) {
            String newText = replaceNameAndEmail(summary.getText());
            for (var edit : edits) {
                newText = edit.apply(newText);
            }
            newSummaries.add(new SummaryTranslation(
                    summary.getId().get(),
                    summary.getActivityId(),
                    summary.getStatusType(),
                    summary.getLanguageCode(),
                    newText));
        }
        activityI18nDao.updateSummaries(newSummaries);
        log.info("Updated {} status summaries for activity {}", newSummaries.size(), activityCode);
    }

    private void updateActivityReadOnlyHintInPlace(FormActivityDef activity) {
        Translation readOnlyTranslation = activity.getReadonlyHintTemplate()
                .getVariables().stream()
                .flatMap(variable -> variable.getTranslations().stream())
                .findFirst().get();
        String newText = replaceNameAndEmail(readOnlyTranslation.getText());
        updateTranslationInPlace(readOnlyTranslation, newText);
        log.info("Updated read-only hint for activity {}", activity.getActivityCode());
    }

    private void updateActivityValidationsInPlace(long activityId, String activityCode, ActivityVersionDto versionDto, List<Edit> edits) {
        List<TemplateVariable> validationVariables = findActivityValidationVariables(activityId, versionDto.getRevStart());
        for (var variable : validationVariables) {
            Translation sub = extractSingleTranslation(variable);
            String newText = replaceNameAndEmail(sub.getText());
            for (var edit : edits) {
                newText = edit.apply(newText);
            }
            updateTranslationInPlace(sub, newText);
        }
        log.info("Updated {} complex validations for activity {}", validationVariables.size(), activityCode);
    }

    private Stream<TemplateVariable> streamTemplateVariables(Iterable<Long> templateIds, long timestamp) {
        return templateDao
                .loadTemplatesByIdsAndTimestamp(templateIds, timestamp)
                .flatMap(template -> template.getVariables().stream());
    }

    protected Stream<TemplateVariable> streamActivityVariables(FormActivityDef activity, ActivityVersionDto versionDto) {
        List<Template> templates = new ArrayList<>();
        Set<Long> templateIdsToLookup = new HashSet<>();

        for (var section : activity.getAllSections()) {
            for (var block : section.getBlocks()) {
                collectBlockTemplates(templates, templateIdsToLookup, block);
            }
        }

        Stream<Template> contentTemplates = templateDao
                .loadTemplatesByIdsAndTimestamp(templateIdsToLookup, versionDto.getRevStart());
        return Stream.concat(contentTemplates, templates.stream())
                .filter(Objects::nonNull)
                .flatMap(template -> template.getVariables().stream());
    }

    private void collectBlockTemplates(List<Template> templates, Set<Long> templateIds, FormBlockDef block) {
        switch (block.getBlockType()) {
            case CONTENT:
                var contentBlock = (ContentBlockDef) block;
                templateIds.add(contentBlock.getTitleTemplateId());
                templateIds.add(contentBlock.getBodyTemplateId());
                break;
            case QUESTION:
                var questionBlock = (QuestionBlockDef) block;
                collectQuestionTemplates(templates, questionBlock.getQuestion());
                break;
            case COMPONENT:
                var componentBlock = (ComponentBlockDef) block;
                collectComponentTemplates(templates, componentBlock);
                break;
            case CONDITIONAL:
                var condBlock = (ConditionalBlockDef) block;
                collectQuestionTemplates(templates, condBlock.getControl());
                for (var nested : condBlock.getNested()) {
                    collectBlockTemplates(templates, templateIds, nested);
                }
                break;
            case GROUP:
                var groupBlock = (GroupBlockDef) block;
                templates.add(groupBlock.getTitleTemplate());
                for (var nested : groupBlock.getNested()) {
                    collectBlockTemplates(templates, templateIds, nested);
                }
                break;
            default:
                break;  // Other block types are not handled.
        }
    }

    private void collectQuestionTemplates(List<Template> templates, QuestionDef question) {
        // Note: not doing question validation templates since those are not needed right now.
        templates.add(question.getPromptTemplate());
        templates.add(question.getTooltipTemplate());
        templates.add(question.getAdditionalInfoHeaderTemplate());
        templates.add(question.getAdditionalInfoFooterTemplate());
        switch (question.getQuestionType()) {
            case BOOLEAN:
                var boolQuestion = (BoolQuestionDef) question;
                templates.add(boolQuestion.getTrueTemplate());
                templates.add(boolQuestion.getFalseTemplate());
                break;
            case DATE:
                var dateQuestion = (DateQuestionDef) question;
                templates.add(dateQuestion.getPlaceholderTemplate());
                break;
            case NUMERIC:
                var numQuestion = (NumericQuestionDef) question;
                templates.add(numQuestion.getPlaceholderTemplate());
                break;
            case DECIMAL:
                var decQuestion = (DecimalQuestionDef) question;
                templates.add(decQuestion.getPlaceholderTemplate());
                break;
            case EQUATION:
                var eqQuestion = (EquationQuestionDef) question;
                templates.add(eqQuestion.getPlaceholderTemplate());
                break;
            case PICKLIST:
                var picklistQuestion = (PicklistQuestionDef) question;
                templates.add(picklistQuestion.getPicklistLabelTemplate());
                for (var group : picklistQuestion.getGroups()) {
                    templates.add(group.getNameTemplate());
                }
                for (var option : picklistQuestion.getAllPicklistOptions()) {
                    templates.add(option.getOptionLabelTemplate());
                    templates.add(option.getDetailLabelTemplate());
                    templates.add(option.getTooltipTemplate());
                    templates.add(option.getNestedOptionsLabelTemplate());
                }
                break;
            case TEXT:
                var textQuestion = (TextQuestionDef) question;
                templates.add(textQuestion.getPlaceholderTemplate());
                templates.add(textQuestion.getConfirmPromptTemplate());
                templates.add(textQuestion.getMismatchMessageTemplate());
                break;
            case COMPOSITE:
                var compQuestion = (CompositeQuestionDef) question;
                templates.add(compQuestion.getAddButtonTemplate());
                templates.add(compQuestion.getAdditionalItemTemplate());
                for (var childQuestion : compQuestion.getChildren()) {
                    collectQuestionTemplates(templates, childQuestion);
                }
                break;
            default:
                break;  // Other types doesn't have additional templates.
        }
    }

    private void collectComponentTemplates(List<Template> templates, ComponentBlockDef component) {
        switch (component.getComponentType()) {
            case MAILING_ADDRESS:
                var mailing = (MailingAddressComponentDef) component;
                templates.add(mailing.getTitleTemplate());
                templates.add(mailing.getSubtitleTemplate());
                break;
            case INSTITUTION: // Fall-through
            case PHYSICIAN:
                var institution = (PhysicianInstitutionComponentDef) component;
                templates.add(institution.getTitleTemplate());
                templates.add(institution.getSubtitleTemplate());
                templates.add(institution.getAddButtonTemplate());
                break;
            default:
                break;  // No other types.
        }
    }

    private QuestionDef parseQuestionDef(Config sourceCfg) {
        QuestionDef def = gson.fromJson(ConfigUtil.toJson(sourceCfg), QuestionDef.class);
        List<JsonValidationError> errors = validator.validateAsJson(def);
        if (!errors.isEmpty()) {
            String msg = errors.stream()
                    .map(JsonValidationError::toDisplayMessage)
                    .collect(Collectors.joining(", "));
            throw new DDPException(String.format(
                    "Question definition with stableId=%s has validation errors: %s",
                    def.getStableId(), msg));
        }
        return def;
    }

    private Map<String, VariableEdit> parseVariableEdits(Config sourceCfg, String key) {
        return sourceCfg.getConfigList(key)
                .stream()
                .map(VariableEdit::new)
                .collect(Collectors.toMap(VariableEdit::getVariableName, Function.identity()));
    }

    private List<Edit> parseEdits(Config sourceCfg, String key) {
        return sourceCfg.getStringList(key)
                .stream()
                .map(Edit::new)
                .collect(Collectors.toList());
    }

    static class VariableEdit {
        private String variableName;
        private List<Edit> edits;

        public VariableEdit(Config cfg) {
            this.variableName = cfg.getString("variable");
            this.edits = cfg.getStringList("edits")
                    .stream()
                    .map(Edit::new)
                    .collect(Collectors.toList());
        }

        public String getVariableName() {
            return variableName;
        }

        public String apply(String text) {
            String newText = replaceNameAndEmail(text);
            for (var edit : edits) {
                newText = edit.apply(newText);
            }
            return newText;
        }
    }

    static class Edit {
        private String oldSnippet;
        private String newSnippet;

        public Edit(String specification) {
            String[] parts = specification.split(ARROW);
            this.oldSnippet = parts[0].trim();
            this.newSnippet = parts[1].trim();
        }

        public String apply(String text) {
            return text.replace(oldSnippet, newSnippet);
        }
    }

    static class RevisionResult {
        private FormActivityDef activity;
        private ActivityVersionDto currentVersionDto;
        private ActivityVersionDto newVersionDto;
        private RevisionMetadata metadata;

        public RevisionResult(FormActivityDef activity, ActivityVersionDto currentVersionDto,
                              ActivityVersionDto newVersionDto, RevisionMetadata metadata) {
            this.activity = activity;
            this.currentVersionDto = currentVersionDto;
            this.newVersionDto = newVersionDto;
            this.metadata = metadata;
        }

        public FormActivityDef getActivity() {
            return activity;
        }

        public ActivityVersionDto getCurrentVersionDto() {
            return currentVersionDto;
        }

        public ActivityVersionDto getNewVersionDto() {
            return newVersionDto;
        }

        public RevisionMetadata getMetadata() {
            return metadata;
        }
    }
}
