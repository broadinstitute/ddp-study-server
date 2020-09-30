package org.broadinstitute.ddp.studybuilder.task;

import static org.broadinstitute.ddp.studybuilder.BuilderUtils.parseAndValidateTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiTemplate;
import org.broadinstitute.ddp.db.dao.JdbiTemplateVariable;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.AnnouncementEventAction;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.model.kit.KitZipCodeRule;
import org.broadinstitute.ddp.model.study.StudySettings;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateTemplatesInPlace implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateTemplatesInPlace.class);

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        User admin = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, admin.getId());

        traverseStudySettings(handle, studyDto.getId());
        traverseKitConfigurations(handle, studyDto.getId());
        traverseEventConfigurations(handle, studyDto.getId());
        traverseActivities(handle, studyDto.getId(), activityBuilder);
    }

    private void traverseStudySettings(Handle handle, long studyId) {
        LOG.info("Comparing templates in study settings...");
        StudySettings settings = handle.attach(StudyDao.class).findSettings(studyId).orElse(null);
        if (settings == null) {
            return;
        }

        Config settingsCfg = studyCfg.getConfig("settings");
        Long inviteErrorTemplateId = settings.getInviteErrorTemplateId();
        extractAndCompare(handle, "", inviteErrorTemplateId, settingsCfg, "inviteErrorTemplate");
    }

    // Note: This currently assumes study has only one config per kit type.
    private void traverseKitConfigurations(Handle handle, long studyId) {
        LOG.info("Comparing templates in kit configurations...");

        Map<String, Config> latestZipCodeRules = new HashMap<>();
        for (var kitCfg : studyCfg.getConfigList("kits")) {
            String kitType = kitCfg.getString("type");
            Config zipCodeRuleCfg = null;
            for (var ruleCfg : kitCfg.getConfigList("rules")) {
                if ("ZIP_CODE".equals(ruleCfg.getString("type"))) {
                    zipCodeRuleCfg = ruleCfg;
                    break;
                }
            }
            if (zipCodeRuleCfg != null) {
                if (latestZipCodeRules.containsKey(kitType)) {
                    throw new DDPException("Currently only supports one config per kit type");
                }
                latestZipCodeRules.put(kitType, zipCodeRuleCfg);
            }
        }

        var kitConfigDao = handle.attach(KitConfigurationDao.class);
        for (var kitConfig : kitConfigDao.findStudyKitConfigurations(studyId)) {
            String kitType = kitConfig.getKitType().getName();
            KitZipCodeRule zipCodeRule = null;
            for (var rule : kitConfig.getRules()) {
                if (KitRuleType.ZIP_CODE.equals(rule.getType())) {
                    zipCodeRule = (KitZipCodeRule) rule;
                    break;
                }
            }
            if (zipCodeRule != null) {
                long ruleId = zipCodeRule.getId();
                Config latestRuleCfg = latestZipCodeRules.get(kitType);
                String prefix = String.format("kit %s zipCodeRule %d", kitType, ruleId);
                extractAndCompare(handle, prefix, zipCodeRule.getErrorMessageTemplateId(), latestRuleCfg, "errorMessageTemplate");
                extractAndCompare(handle, prefix, zipCodeRule.getWarningMessageTemplateId(), latestRuleCfg, "warningMessageTemplate");
            }
        }
    }

    // Note: This currently assumes only ANNOUNCEMENT event actions has templates.
    private void traverseEventConfigurations(Handle handle, long studyId) {
        LOG.info("Comparing templates in event configurations...");

        Map<String, Config> latestAnnouncementEvents = new HashMap<>();
        for (var eventCfg : studyCfg.getConfigList("events")) {
            if ("ANNOUNCEMENT".equals(eventCfg.getString("action.type"))) {
                String eventKey = hashEvent(eventCfg);
                latestAnnouncementEvents.put(eventKey, eventCfg);
            }
        }

        var eventDao = handle.attach(EventDao.class);
        var templateDao = handle.attach(TemplateDao.class);

        for (var eventConfig : eventDao.getAllEventConfigurationsByStudyId(studyId)) {
            if (EventActionType.ANNOUNCEMENT.equals(eventConfig.getEventActionType())) {
                String eventKey = hashEvent(handle, eventConfig);
                Config eventCfg = latestAnnouncementEvents.get(eventKey);

                long templateId = ((AnnouncementEventAction) eventConfig.getEventAction()).getMessageTemplateId();
                Template current = templateDao.loadTemplateById(templateId);
                Template latest = parseAndValidateTemplate(eventCfg, "action.msgTemplate");

                String tag = String.format("event %s announcementMessageTemplate %d", eventKey, templateId);
                compareTemplate(handle, tag, current, latest);
            }
        }
    }

    // Best-effort attempt at making a unique identifier for event configuration.
    private String hashEvent(Config eventCfg) {
        return String.format("%s-%d",
                EventBuilder.triggerAsStr(eventCfg.getConfig("trigger")),
                eventCfg.getInt("order"));
    }

    // Best-effort attempt at making a unique identifier for event configuration.
    private String hashEvent(Handle handle, EventConfiguration eventConfig) {
        return String.format("%s-%d",
                EventBuilder.triggerAsStr(handle, eventConfig.getEventTrigger()),
                eventConfig.getExecutionOrder());
    }

    // Note: This assumes that what's in the database and in the configuration files are the same in terms of structure.
    private void traverseActivities(Handle handle, long studyId, ActivityBuilder activityBuilder) {
        var activityDao = handle.attach(ActivityDao.class);
        var jdbiActivity = handle.attach(JdbiActivity.class);
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);

        for (Config activityCfg : studyCfg.getConfigList("activities")) {
            Config definition = activityBuilder.readDefinitionConfig(activityCfg.getString("filepath"));
            String activityCode = definition.getString("activityCode");
            String versionTag = definition.getString("versionTag");

            ActivityDto activityDto = jdbiActivity.findActivityByStudyIdAndCode(studyId, activityCode).get();
            ActivityVersionDto versionDto = jdbiActVersion.findByActivityCodeAndVersionTag(studyId, activityCode, versionTag).get();
            FormActivityDef activity = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, versionDto);

            traverseActivity(handle, activityCode, definition, activity);
        }
    }

    private void traverseActivity(Handle handle, String activityCode, Config definition, FormActivityDef activity) {
        LOG.info("Comparing templates in activity {}...", activityCode);

        extractAndCompare(handle, "", activity.getLastUpdatedTextTemplate(), definition, "lastUpdatedTextTemplate");
        extractAndCompare(handle, "", activity.getReadonlyHintTemplate(), definition, "readonlyHintTemplate");

        List<FormSectionDef> sections = activity.getAllSections();
        List<Config> sectionCfgs = new ArrayList<>();
        if (definition.hasPath("introduction")) {
            sectionCfgs.add(definition.getConfig("introduction"));
        }
        sectionCfgs.addAll(definition.getConfigList("sections"));
        if (definition.hasPath("closing")) {
            sectionCfgs.add(definition.getConfig("closing"));
        }
        if (sections.size() != sectionCfgs.size()) {
            throw new DDPException("Activity " + activityCode + " number of sections does not match");
        }

        for (int i = 0; i < sections.size(); i++) {
            traverseSection(handle, i + 1, sectionCfgs.get(i), sections.get(i));
        }
    }

    private void traverseSection(Handle handle, int sectionNum, Config sectionCfg, FormSectionDef section) {
        String prefix = String.format("section %d", sectionNum);
        extractAndCompare(handle, prefix, section.getNameTemplate(), sectionCfg, "nameTemplate");

        List<FormBlockDef> blocks = section.getBlocks();
        List<Config> blockCfgs = List.copyOf(sectionCfg.getConfigList("blocks"));
        if (blocks.size() != blockCfgs.size()) {
            throw new DDPException("Section " + sectionNum + " number of blocks does not match");
        }

        for (int i = 0; i < blocks.size(); i++) {
            traverseBlock(handle, sectionNum, i + 1, null, blockCfgs.get(i), blocks.get(i));
        }
    }

    private void traverseBlock(Handle handle, int sectionNum, int blockNum, Integer nestedNum, Config blockCfg, FormBlockDef block) {
        switch (block.getBlockType()) {
            case CONTENT:
                traverseContent(handle, sectionNum, blockNum, nestedNum, blockCfg, (ContentBlockDef) block);
                break;
            case QUESTION:
                traverseQuestion(handle, blockCfg.getConfig("question"), ((QuestionBlockDef) block).getQuestion());
                break;
            case COMPONENT:
                traverseComponent(handle, sectionNum, blockNum, nestedNum, blockCfg, (ComponentBlockDef) block);
                break;
            case CONDITIONAL:
                ConditionalBlockDef condBlock = (ConditionalBlockDef) block;
                traverseQuestion(handle, blockCfg.getConfig("control"), condBlock.getControl());

                List<FormBlockDef> condNested = condBlock.getNested();
                List<Config> condNestedCfgs = List.copyOf(blockCfg.getConfigList("nested"));
                if (condNested.size() != condNestedCfgs.size()) {
                    throw new DDPException("Section " + sectionNum + " conditional block "
                            + blockNum + " number of nested blocks does not match");
                }

                for (int i = 0; i < condNested.size(); i++) {
                    traverseBlock(handle, sectionNum, blockNum, i + 1, condNestedCfgs.get(i), condNested.get(i));
                }

                break;
            case GROUP:
                GroupBlockDef groupBlock = (GroupBlockDef) block;
                String type = groupBlock.getBlockType().name();
                String prefix = String.format("section %d block %d %s", sectionNum, blockNum, type);
                extractAndCompare(handle, prefix, groupBlock.getTitleTemplate(), blockCfg, "title");

                List<FormBlockDef> groupNested = groupBlock.getNested();
                List<Config> groupNestedCfgs = List.copyOf(blockCfg.getConfigList("nested"));
                if (groupNested.size() != groupNestedCfgs.size()) {
                    throw new DDPException("Section " + sectionNum + " group block "
                            + blockNum + " number of nested blocks does not match");
                }

                for (int i = 0; i < groupNested.size(); i++) {
                    traverseBlock(handle, sectionNum, blockNum, i + 1, groupNestedCfgs.get(i), groupNested.get(i));
                }

                break;
            default:
                throw new DDPException("Unhandled block type: " + block.getBlockType());
        }
    }

    // Note: for now, we're querying the content block templates here.
    private void traverseContent(Handle handle, int sectionNum, int blockNum, Integer nestedNum, Config blockCfg, ContentBlockDef block) {
        String type = block.getBlockType().name();
        String prefix = String.format("section %d block %d%s %s",
                sectionNum, blockNum, nestedNum == null ? "" : " nested " + nestedNum, type);
        extractAndCompare(handle, prefix, block.getTitleTemplateId(), blockCfg, "titleTemplate");
        extractAndCompare(handle, prefix, block.getBodyTemplateId(), blockCfg, "bodyTemplate");
    }

    private void traverseComponent(Handle handle, int sectionNum, int blockNum, Integer nestNum, Config blockCfg, ComponentBlockDef block) {
        String prefix = String.format("section %d block %d%s",
                sectionNum, blockNum, nestNum == null ? "" : " nested " + nestNum);
        switch (block.getComponentType()) {
            case MAILING_ADDRESS:
                MailingAddressComponentDef addressComponent = (MailingAddressComponentDef) block;
                prefix = prefix + " " + addressComponent.getComponentType().name();
                extractAndCompare(handle, prefix, addressComponent.getTitleTemplate(), blockCfg, "titleTemplate");
                extractAndCompare(handle, prefix, addressComponent.getSubtitleTemplate(), blockCfg, "subtitleTemplate");
                break;
            case PHYSICIAN:
                // Fall-through
            case INSTITUTION:
                PhysicianInstitutionComponentDef institutionComponent = (PhysicianInstitutionComponentDef) block;
                prefix = prefix + " " + institutionComponent.getInstitutionType().name();
                extractAndCompare(handle, prefix, institutionComponent.getTitleTemplate(), blockCfg, "titleTemplate");
                extractAndCompare(handle, prefix, institutionComponent.getSubtitleTemplate(), blockCfg, "subtitleTemplate");
                extractAndCompare(handle, prefix, institutionComponent.getAddButtonTemplate(), blockCfg, "addButtonTemplate");
                break;
            default:
                throw new DDPException("Unhandled component type: " + block.getComponentType());
        }
    }

    private void traverseQuestion(Handle handle, Config questionCfg, QuestionDef question) {
        String prefix = String.format("question %s", question.getStableId());

        extractAndCompare(handle, prefix, question.getPromptTemplate(), questionCfg, "promptTemplate");
        extractAndCompare(handle, prefix, question.getTooltipTemplate(), questionCfg, "tooltipTemplate");
        extractAndCompare(handle, prefix, question.getAdditionalInfoHeaderTemplate(), questionCfg, "additionalInfoHeaderTemplate");
        extractAndCompare(handle, prefix, question.getAdditionalInfoFooterTemplate(), questionCfg, "additionalInfoFooterTemplate");

        switch (question.getQuestionType()) {
            case AGREEMENT:
                // Nothing else to do.
                break;
            case BOOLEAN:
                BoolQuestionDef boolQuestion = (BoolQuestionDef) question;
                extractAndCompare(handle, prefix, boolQuestion.getTrueTemplate(), questionCfg, "trueTemplate");
                extractAndCompare(handle, prefix, boolQuestion.getFalseTemplate(), questionCfg, "falseTemplate");
                break;
            case DATE:
                DateQuestionDef dateQuestion = (DateQuestionDef) question;
                extractAndCompare(handle, prefix, dateQuestion.getPlaceholderTemplate(), questionCfg, "placeholderTemplate");
                break;
            case NUMERIC:
                NumericQuestionDef numericQuestion = (NumericQuestionDef) question;
                extractAndCompare(handle, prefix, numericQuestion.getPlaceholderTemplate(), questionCfg, "placeholderTemplate");
                break;
            case PICKLIST:
                traversePicklistQuestion(handle, questionCfg, (PicklistQuestionDef) question);
                break;
            case TEXT:
                TextQuestionDef textQuestion = (TextQuestionDef) question;
                extractAndCompare(handle, prefix, textQuestion.getPlaceholderTemplate(), questionCfg, "placeholderTemplate");
                extractAndCompare(handle, prefix, textQuestion.getConfirmPromptTemplate(), questionCfg, "confirmPromptTemplate");
                extractAndCompare(handle, prefix, textQuestion.getMismatchMessageTemplate(), questionCfg, "mismatchMessageTemplate");
                break;
            case COMPOSITE:
                traverseCompositeQuestion(handle, questionCfg, (CompositeQuestionDef) question);
                break;
            default:
                throw new DDPException("Unhandled question type: " + question.getQuestionType());
        }

        traverseQuestionValidations(handle, questionCfg, question);
    }

    private void traversePicklistQuestion(Handle handle, Config questionCfg, PicklistQuestionDef question) {
        String questionStableId = question.getStableId();
        String prefix = String.format("question %s", questionStableId);
        extractAndCompare(handle, prefix, question.getPicklistLabelTemplate(), questionCfg, "picklistLabelTemplate");

        List<PicklistGroupDef> groups = question.getGroups();
        List<Config> groupCfgs = List.copyOf(questionCfg.getConfigList("groups"));
        if (groups.size() != groupCfgs.size()) {
            throw new DDPException("Question " + questionStableId + " number of picklist groups does not match");
        }

        Map<String, Config> allOptionCfgs = new HashMap<>();
        for (var optionCfg : questionCfg.getConfigList("picklistOptions")) {
            allOptionCfgs.put(optionCfg.getString("stableId"), optionCfg);
        }

        for (int i = 0; i < groups.size(); i++) {
            PicklistGroupDef group = groups.get(i);
            Config groupCfg = groupCfgs.get(i);
            prefix = String.format("question %s group %d", questionStableId, i + 1);
            extractAndCompare(handle, prefix, group.getNameTemplate(), groupCfg, "nameTemplate");
            for (var optionCfg : groupCfg.getConfigList("options")) {
                allOptionCfgs.put(optionCfg.getString("stableId"), optionCfg);
            }
        }

        Map<String, PicklistOptionDef> allOptions = question.getAllPicklistOptions().stream()
                .collect(Collectors.toMap(PicklistOptionDef::getStableId, Function.identity()));
        if (allOptions.size() != allOptionCfgs.size()) {
            throw new DDPException("Question " + questionStableId + " number of picklist options does not match");
        }

        List<String> stableIds = allOptions.keySet().stream().sorted().collect(Collectors.toList());
        for (var stableId : stableIds) {
            PicklistOptionDef option = allOptions.get(stableId);
            Config optionCfg = allOptionCfgs.get(stableId);
            prefix = String.format("question %s option %s", questionStableId, stableId);
            extractAndCompare(handle, prefix, option.getOptionLabelTemplate(), optionCfg, "optionLabelTemplate");
            extractAndCompare(handle, prefix, option.getDetailLabelTemplate(), optionCfg, "detailLabelTemplate");
            extractAndCompare(handle, prefix, option.getTooltipTemplate(), optionCfg, "tooltipTemplate");
        }
    }

    private void traverseCompositeQuestion(Handle handle, Config questionCfg, CompositeQuestionDef question) {
        String prefix = String.format("question %s", question.getStableId());

        extractAndCompare(handle, prefix, question.getAddButtonTemplate(), questionCfg, "addButtonTemplate");
        extractAndCompare(handle, prefix, question.getAdditionalItemTemplate(), questionCfg, "additionalItemTemplate");

        List<QuestionDef> children = question.getChildren();
        List<Config> childrenCfgs = List.copyOf(questionCfg.getConfigList("children"));
        if (children.size() != childrenCfgs.size()) {
            throw new DDPException("Question " + question.getStableId() + " number of child questions does not match");
        }

        for (int i = 0; i < children.size(); i++) {
            traverseQuestion(handle, childrenCfgs.get(i), children.get(i));
        }
    }

    private void traverseQuestionValidations(Handle handle, Config questionCfg, QuestionDef question) {
        String questionStableId = question.getStableId();

        List<RuleDef> rules = question.getValidations();
        List<Config> ruleCfgs = List.copyOf(questionCfg.getConfigList("validations"));
        if (rules.size() != ruleCfgs.size()) {
            throw new DDPException("Question " + questionStableId + " number of validation rules does not match");
        }

        for (int i = 0; i < rules.size(); i++) {
            RuleDef rule = rules.get(i);
            Config ruleCfg = ruleCfgs.get(i);
            String prefix = String.format("question %s rule %s", questionStableId, rule.getRuleType().name());
            extractAndCompare(handle, prefix, rule.getHintTemplateId(), ruleCfg, "hintTemplate");
        }
    }

    private void extractAndCompare(Handle handle, String prefix, Long currentTemplateId, Config cfg, String key) {
        if (currentTemplateId != null) {
            Template current = handle.attach(TemplateDao.class).loadTemplateById(currentTemplateId);
            extractAndCompare(handle, prefix, current, cfg, key);
        }
    }

    private void extractAndCompare(Handle handle, String prefix, Template current, Config cfg, String key) {
        if (current != null) {
            Template latest = parseAndValidateTemplate(cfg, key);
            String tag = String.format("%s %d", key, current.getTemplateId());
            if (prefix != null && !prefix.isBlank()) {
                tag = prefix + " " + tag;
            }
            compareTemplate(handle, tag, current, latest);
        }
    }

    private void compareTemplate(Handle handle, String tag, Template current, Template latest) {
        var jdbiTemplate = handle.attach(JdbiTemplate.class);
        long templateId = current.getTemplateId();
        long revisionId = current.getRevisionId().get();

        String currentText = current.getTemplateText();
        String latestText = latest.getTemplateText();
        if (!currentText.equals(latestText)) {
            jdbiTemplate.update(
                    templateId,
                    current.getTemplateCode(),
                    current.getTemplateType(),
                    latestText,
                    revisionId);
            LOG.info("[{}] updated template text", tag);
        }

        Map<String, TemplateVariable> latestVariables = new HashMap<>();
        for (var latestVar : latest.getVariables()) {
            latestVariables.put(latestVar.getName(), latestVar);
        }

        for (var currentVar : current.getVariables()) {
            TemplateVariable latestVar = latestVariables.remove(currentVar.getName());
            if (latestVar != null) {
                compareVariable(handle, tag, revisionId, currentVar, latestVar);
            } else {
                deleteVariable(handle, tag, currentVar);
            }
        }

        // Any remaining variables means new variables, so add them.
        for (var latestVar : latestVariables.values()) {
            addVariable(handle, tag, templateId, revisionId, latestVar);
        }
    }

    private void compareVariable(Handle handle, String tag, long revisionId, TemplateVariable current, TemplateVariable latest) {
        var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);
        String variableName = current.getName();
        long variableId = current.getId().get();

        Map<String, String> latestTranslations = new HashMap<>();
        for (var translation : latest.getTranslations()) {
            latestTranslations.put(translation.getLanguageCode(), translation.getText());
        }

        for (var translation : current.getTranslations()) {
            String language = translation.getLanguageCode();
            String currentText = translation.getText();
            String latestText = latestTranslations.remove(language);
            if (!currentText.equals(latestText)) {
                boolean updated = jdbiVariableSubstitution.update(
                        translation.getId().get(),
                        translation.getRevisionId().get(),
                        language,
                        latestText);
                if (updated) {
                    LOG.info("[{}] variable {} language {}: updated substitution", tag, variableName, language);
                } else {
                    throw new DDPException(String.format(
                            "Could not update substitution for %s variable %s language %s",
                            tag, variableName, language));
                }
            }
        }

        // Any remaining translations means new languages, so add them.
        for (var language : latestTranslations.keySet()) {
            String text = latestTranslations.get(language);
            jdbiVariableSubstitution.insert(language, text, revisionId, variableId);
            LOG.info("[{}] variable {} language {}: inserted substitution", tag, variableName, language);
        }
    }

    private void deleteVariable(Handle handle, String tag, TemplateVariable variable) {
        var jdbiTemplateVariable = handle.attach(JdbiTemplateVariable.class);
        var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);
        String variableName = variable.getName();
        long variableId = variable.getId().get();

        for (var translation : variable.getTranslations()) {
            String language = translation.getLanguageCode();
            boolean deleted = jdbiVariableSubstitution.delete(translation.getId().get());
            if (deleted) {
                LOG.info("[{}] variable {} language {}: deleted substitution", tag, variableName, language);
            } else {
                throw new DDPException(String.format(
                        "Could not delete substitution for %s variable %s language %s",
                        tag, variableName, language));
            }
        }

        boolean deleted = jdbiTemplateVariable.delete(variableId);
        if (deleted) {
            LOG.info("[{}] deleted variable {}", tag, variableName);
        } else {
            throw new DDPException(String.format("Could not delete %s variable %s", tag, variableName));
        }
    }

    private void addVariable(Handle handle, String tag, long templateId, long revisionId, TemplateVariable variable) {
        var jdbiTemplateVariable = handle.attach(JdbiTemplateVariable.class);
        var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);
        String variableName = variable.getName();

        if (I18nTemplateConstants.DDP.equals(variableName) || I18nTemplateConstants.LAST_UPDATED.equals(variableName)) {
            throw new DaoException("Variable name '" + variableName + "' is not allowed");
        }

        long variableId = jdbiTemplateVariable.insertVariable(templateId, variableName);
        LOG.info("[{}] inserted variable {}", tag, variableName);

        for (var translation : variable.getTranslations()) {
            String language = translation.getLanguageCode();
            jdbiVariableSubstitution.insert(language, translation.getText(), revisionId, variableId);
            LOG.info("[{}] variable {} language {}: inserted substitution",
                    tag, variableName, language);
        }
    }
}
