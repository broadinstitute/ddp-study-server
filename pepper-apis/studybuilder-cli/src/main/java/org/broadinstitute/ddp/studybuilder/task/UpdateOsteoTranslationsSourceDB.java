package org.broadinstitute.ddp.studybuilder.task;

import com.google.common.base.Functions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityValidation;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityValidationDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DecimalQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.EquationQuestionDef;
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
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * General task to iterate through all templates in a study and update them in-place.
 * For each activity of the study, iterate through each section, block, each template and for each template, this will
 * compare the template text, the variables, and the variable translations, updating these things in-place without revisioning.
 * Source of truth is DB.
 * Iterate through each translation variable in DB and find matching variable in i18n.es.conf
 * and add/update the translation
 */
@Slf4j
@NoArgsConstructor
public class UpdateOsteoTranslationsSourceDB implements CustomTask {
    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private List<String> variablesToSkip;
    private Config i18nCfgEn;
    private Config i18nCfgEs;
    private Map<String, Map> allActTransMapEN;
    private Map<String, Map> allActTransMapES;
    private Map<String, String> missingTransVars = new TreeMap<>();
    private String enFilePath = "studybuilder-cli/studies/osteo/i18n/en.conf";
    private String esFilePath = "studybuilder-cli/studies/osteo/i18n/es.conf";
    Gson gson = new Gson();
    Type typeObject = new TypeToken<HashMap>() {
    }.getType();


    public UpdateOsteoTranslationsSourceDB(List<String> variablesToSkip) {
        this.variablesToSkip = variablesToSkip;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK:: UpdateTranslationsSourceDB ");

        //load i18n
        //todo pass file path as params
        //need controls to block running on studies accidentally
        i18nCfgEn = ConfigFactory.parseFile(Paths.get(enFilePath).toFile());
        i18nCfgEs = ConfigFactory.parseFile(Paths.get(esFilePath).toFile());

        //Maps to save ALL translations for any verification
        allActTransMapEN = new TreeMap<String, Map>();
        allActTransMapES = new TreeMap<String, Map>();

        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        User admin = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, admin.getId());

        traverseEventConfigurations(handle, studyDto.getId());
        traverseActivities(handle, studyDto.getId(), activityBuilder);
        String gsonDataEN = gson.toJson(allActTransMapEN, typeObject);
        //log.info("Reverse engineered EN translation file");
        //log.info(gsonDataEN);

        //log.info("Reverse engineered ES translation file");
        String gsonDataES = gson.toJson(allActTransMapES, typeObject);
        //log.info(gsonDataES);
    }

    // Note: This currently assumes only ANNOUNCEMENT event actions has templates.
    private void traverseEventConfigurations(Handle handle, long studyId) {
        log.info("Comparing templates in event configurations...");

        var eventDao = handle.attach(EventDao.class);
        var templateDao = handle.attach(TemplateDao.class);

        //watch out for variables with same name referring to diff activities //todo
        for (var eventConfig : eventDao.getAllEventConfigurationsByStudyId(studyId)) {
            if (EventActionType.ANNOUNCEMENT.equals(eventConfig.getEventActionType())) {
                String key = "announcements";
                long timestamp = Instant.now().toEpochMilli();
                long templateId = ((AnnouncementEventAction) eventConfig.getEventAction()).getMessageTemplateId();
                Template current = templateDao.loadTemplateByIdAndTimestamp(templateId, timestamp);
                String tag = String.format("event announcementMessageTemplate %d", templateId);
                //handle same variable names in diff activities like
                //osteo_thank_you_announcement_p1 and osteo_thank_you_announcement_p2 for child
                //get activityId from eventConfig.eventConfigurationDto
                EventConfigurationDto eventConfigurationDto = eventConfig.getEventTrigger().getEventConfigurationDto();
                Long linkedActivityId = eventConfigurationDto.getActivityStatusTriggerStudyActivityId();
                if (linkedActivityId != null) {
                    //find activity code
                    ActivityDto activityDto = handle.attach(JdbiActivity.class).queryActivityById(linkedActivityId);
                    if (activityDto.getActivityCode().contains("MINOR") || activityDto.getActivityCode().contains("PEDIATRIC")) {
                        key = "announcements_child";
                    }
                }
                compareTemplate(handle, tag, current, key);
            }
        }
    }

    //Source of Truth is DB
    //Read DB for activity's latest version and get translations from current 18n.<lc>.conf and update in DB
    private void traverseActivities(Handle handle, long studyId, ActivityBuilder activityBuilder) {
        var activityDao = handle.attach(ActivityDao.class);
        var jdbiActivity = handle.attach(JdbiActivity.class);
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);
        var jdbiActivityValidation = handle.attach(JdbiActivityValidation.class);
        var templateDao = handle.attach(TemplateDao.class);

        //load study config from DB. All activities latest version
        List<ActivityDto> allActivities = jdbiActivity.findOrderedDtosByStudyId(studyId);
        log.info("Activity count: {} ", allActivities.size());
        for (ActivityDto activityDto : allActivities) {
            //get latest version
            ActivityVersionDto versionDto = jdbiActVersion.getActiveVersion(activityDto.getActivityId()).get();
            FormActivityDef activity = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, versionDto);
            log.info("#activity : {}  .. version: {}", activityDto.getActivityCode(), versionDto.getVersionTag());

            //tailored to osteo for now
            if (activityDto.getActivityCode().equalsIgnoreCase("LOVEDONE")) {

                traverseActivity(handle, activity);

                compareNamingDetails(handle, activity.getActivityCode().toLowerCase(), activity.getActivityId(), versionDto);
                //todo handle activity summaries

                Template hintTemplate = activity.getReadonlyHintTemplate();
                compareTemplate(handle, activity.getTag(), hintTemplate, activity.getActivityCode());
                Template lastUpdatedTemplate = activity.getReadonlyHintTemplate();
                compareTemplate(handle, activity.getTag(), lastUpdatedTemplate, activity.getActivityCode());

                //handle activity level validations / errorMessages / messageTempates
                List<ActivityValidationDto> validationDtos = jdbiActivityValidation._findByActivityId(activityDto.getActivityId());
                for (ActivityValidationDto validationDto : validationDtos) {
                    //NOTE: ActivityDef does not include messageTemplate info.. todo in future
                    //load message template info and variables info
                    Long errorMsgTemplateId = validationDto.getErrorMessageTemplateId();
                    if (errorMsgTemplateId != null) {
                        Template messageTemplate = templateDao.loadTemplateByIdAndTimestamp(errorMsgTemplateId, versionDto.getRevStart());
                        compareTemplate(handle, activity.getTag(), messageTemplate, "activity_validations");
                    }
                }
            }

            //manual check in logs if any translations are missing  in es.json
            log.info("MISSING translation vars in es.conf: {}", activityDto.getActivityCode());
            String gsonDataMiss = gson.toJson(missingTransVars, typeObject);
            log.info(gsonDataMiss);
        }
    }

    void traverseActivity(Handle handle, FormActivityDef activity) {
        List<FormSectionDef> sections = activity.getAllSections();
        log.info("ACTIVITY: {} .. DB sections: {}  ", activity.getActivityCode(), sections.size());

        for (int i = 0; i < sections.size(); i++) {
            traverseSection(handle, i, sections.get(i), activity);
        }
    }

    public void compareNamingDetails(Handle handle, String activityCode, long activityId, ActivityVersionDto versionDto) {
        //osteo specific .. cleanup later
        if (activityCode.equalsIgnoreCase("prequal")) {
            return;
        }
        log.info("comparing naming details for activity : {}", activityCode);
        var activityI18nDao = handle.attach(ActivityI18nDao.class);
        Map<String, ActivityI18nDetail> currentDetails = activityI18nDao
                .findDetailsByActivityIdAndTimestamp(activityId, versionDto.getRevStart())
                .stream()
                .collect(Collectors.toMap(ActivityI18nDetail::getIsoLangCode, Functions.identity()));

        ActivityI18nDetail currentES = currentDetails.get("es");
        ActivityI18nDetail currentEN = currentDetails.get("en");
        //todo if no es .. get en details and
        ActivityI18nDetail latestDetails =
                buildLatestNamingDetail(activityId, versionDto.getRevId(), activityCode, currentES != null ? currentES : currentEN);
        if (latestDetails == null) {
            log.warn("NO Latest Details null for activity: {}", activityCode);
            return;
        }

        if (currentES == null) {
            log.info("inserting new naming details for activity : {}", activityCode);
            //insert new
            List<ActivityI18nDetail> newDetails = Collections.singletonList(latestDetails);
            activityI18nDao.insertDetails(newDetails);
            log.info("NEW: Inserted naming details for activity {} .. language: {}", activityCode, "es");
        } else {
            log.info("comparing to update naming details for activity : {}", activityCode);
            if (!currentES.equals(latestDetails)) {
                log.info("updating naming details for activity : {}", activityCode);
                activityI18nDao.updateDetails(Collections.singletonList(latestDetails));
                log.info("Updated naming details for activity {} .. language: {}", activityCode, "es");
            }
        }
    }

    private ActivityI18nDetail buildLatestNamingDetail(long activityId, long revisionId, String activityCode,
                                                       ActivityI18nDetail current) {

        //for now tailored to osteo because osteo es.json elements dont match with activity names
        //todo change later
        String key = activityCode;
        if (activityCode.equalsIgnoreCase("CONSENT_ASSENT") || activityCode.equalsIgnoreCase("PARENTAL_CONSENT")) {
            key = "parental";
        }
        if (activityCode.equalsIgnoreCase("CONSENT_ADDENDUM")) {
            key = "somatic_consent_addendum";
        }
        if (activityCode.equalsIgnoreCase("CONSENT_ADDENDUM_PEDIATRIC")) {
            key = "somatic_consent_addendum_pediatric";
        }
        if (activityCode.equalsIgnoreCase("ABOUTYOU")) {
            key = "about_you";
        }
        if (activityCode.equalsIgnoreCase("ABOUTCHILD")) {
            key = "about_child";
        }

        String name = null;
        if (i18nCfgEs.hasPath(key + "." + "name")) {
            name = i18nCfgEs.getString(key + "." + "name");
        }
        if (name == null) {
            return null;
        }

        String secondName = null;
        if (i18nCfgEs.hasPath(key + "." + "secondName")) {
            secondName = i18nCfgEs.getString(key + "." + "secondName");
        }

        String title = null;
        if (i18nCfgEs.hasPath(key + "." + "title")) {
            title = i18nCfgEs.getString(key + "." + "title");
        }
        log.info("Title: {} for activity: {} .. key: {} ", title, activityCode, key);
        String subTitle = null;
        if (i18nCfgEs.hasPath(key + "." + "subTitle")) {
            subTitle = i18nCfgEs.getString(key + "." + "subTitle");
        }
        String description = null;
        if (i18nCfgEs.hasPath(key + "." + "subTitle")) {
            description = i18nCfgEs.getString(key + "." + "description");
        }

        ActivityI18nDetail latest = new ActivityI18nDetail(
                current.getId(),
                activityId,
                LanguageStore.get("es").getId(),
                "es",
                name,
                secondName,
                title,
                subTitle,
                description,
                current == null ? revisionId : current.getRevisionId());
        return latest;
    }

    public void traverseSection(Handle handle, int sectionNum, FormSectionDef section, ActivityDef activity) {
        String activityCode = activity.getActivityCode();
        Template sectionNameTemplate = section.getNameTemplate();
        if (sectionNameTemplate != null) {
            handleSectionNames(handle, sectionNum, activityCode, sectionNameTemplate);
        }

        List<FormBlockDef> blocks = section.getBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            traverseBlock(handle, sectionNum, i + 1, null, blocks.get(i), activityCode);
        }
    }

    private void handleSectionNames(Handle handle, int sectionNum, String activityCode, Template sectionNameTemplate) {
        if (sectionNameTemplate.getTemplateText().startsWith("$")) {
            //todo section name comparison . 
            //compareTemplate(handle, prefix, sectionNameTemplate, activityCode);
        } else {
            //need to add translations
            //load both en and es for activity_code + section + sectionNum
            String varName = activityCode.toLowerCase() + "_s" + sectionNum + "_name";
            String enText = sectionNameTemplate.getTemplateText();
            String key = activityCode.toLowerCase() + "." + varName;
            if (activityCode.equalsIgnoreCase("PARENTAL_CONSENT")
                    || activityCode.equalsIgnoreCase("CONSENT_ASSENT")) {
                key = "parental." + varName;
            }
            if (activityCode.contains("CONSENT_ADDENDUM_PEDIATRIC")) {
                key = "somatic_consent_addendum_pediatric." + varName;
            } else {
                if (activityCode.contains("CONSENT_ADDENDUM")) {
                    key = "somatic_consent_addendum." + varName;
                }
            }
            long revId = sectionNameTemplate.getRevisionId().get();
            log.info("EN text: {}  .. version: {} .. key: {} ", enText, revId, key);
            String esText = i18nCfgEs.getString(key);
            log.info("esText: {}   ", esText);

            var templateDao = handle.attach(TemplateDao.class);
            long varId = templateDao.getJdbiTemplateVariable().insertVariable(sectionNameTemplate.getTemplateId(), varName);
            log.info("NEW section name: inserted varId: {} .. revision: {} ..", varId, revId);
            //update template with new varId
            templateDao.getJdbiTemplate().update(sectionNameTemplate.getTemplateId(), sectionNameTemplate.getTemplateCode(),
                    sectionNameTemplate.getTemplateType(), "$" + varName, revId);
            //insert translations
            var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);
            jdbiVariableSubstitution.insert("en", enText, revId, varId);
            jdbiVariableSubstitution.insert("es", esText, revId, varId);
        }
    }

    private void traverseBlock(Handle handle, int sectionNum, int blockNum, Integer nestedNum,
                               FormBlockDef block, String activityCode) {
        switch (block.getBlockType()) {
            case CONTENT:
                traverseContent(handle, sectionNum, blockNum, nestedNum, (ContentBlockDef) block, activityCode);
                break;
            case QUESTION:
                traverseQuestion(handle, ((QuestionBlockDef) block).getQuestion(), activityCode);
                break;
            case COMPONENT:
                traverseComponent(handle, sectionNum, blockNum, nestedNum, (ComponentBlockDef) block, activityCode);
                break;
            case ACTIVITY:
                traverseNestedActivity(handle, sectionNum, blockNum, (NestedActivityBlockDef) block, activityCode);
                break;
            case CONDITIONAL:
                ConditionalBlockDef condBlock = (ConditionalBlockDef) block;
                traverseQuestion(handle, condBlock.getControl(), activityCode);

                List<FormBlockDef> condNested = condBlock.getNested();
                for (int i = 0; i < condNested.size(); i++) {
                    traverseBlock(handle, sectionNum, blockNum, i + 1, condNested.get(i), activityCode);
                }

                break;
            case GROUP:
                GroupBlockDef groupBlock = (GroupBlockDef) block;
                String type = groupBlock.getBlockType().name();
                String prefix = String.format("section %d block %d %s", sectionNum, blockNum, type);
                compareTemplate(handle, prefix, groupBlock.getTitleTemplate(), activityCode);

                List<FormBlockDef> groupNested = groupBlock.getNested();

                for (int i = 0; i < groupNested.size(); i++) {
                    traverseBlock(handle, sectionNum, blockNum, i + 1, groupNested.get(i), activityCode);
                }

                break;
            default:
                throw new DDPException("Unhandled block type: " + block.getBlockType());
        }
    }


    private void traverseNestedActivity(Handle handle, int sectionNum, int blockNum, NestedActivityBlockDef block, String activityCode) {
        String type = block.getBlockType().name();
        String prefix = String.format("section %d block %d %s", sectionNum, blockNum, type);
        compareTemplate(handle, prefix, block.getAddButtonTemplate(), activityCode);
    }

    private void traverseContent(Handle handle, int sectionNum, int blockNum, Integer nestedNum,
                                 ContentBlockDef block, String activityCode) {
        String type = block.getBlockType().name();
        String prefix = String.format("section %d block %d%s %s",
                sectionNum, blockNum, nestedNum == null ? "" : " nested " + nestedNum, type);
        compareTemplate(handle, prefix, block.getTitleTemplate(), activityCode);
        compareTemplate(handle, prefix, block.getBodyTemplate(), activityCode);
    }

    private void traverseComponent(Handle handle, int sectionNum, int blockNum, Integer nestNum,
                                   ComponentBlockDef block, String activityCode) {
        String prefix = String.format("section %d block %d%s",
                sectionNum, blockNum, nestNum == null ? "" : " nested " + nestNum);
        switch (block.getComponentType()) {
            case MAILING_ADDRESS:
                MailingAddressComponentDef addressComponent = (MailingAddressComponentDef) block;
                prefix = prefix + " " + addressComponent.getComponentType().name();
                compareTemplate(handle, prefix, addressComponent.getTitleTemplate(), activityCode);
                compareTemplate(handle, prefix, addressComponent.getSubtitleTemplate(), activityCode);
                break;
            case PHYSICIAN:
                // Fall-through
            case INSTITUTION:
                PhysicianInstitutionComponentDef institutionComponent = (PhysicianInstitutionComponentDef) block;
                prefix = prefix + " " + institutionComponent.getInstitutionType().name();
                compareTemplate(handle, prefix, institutionComponent.getTitleTemplate(), activityCode);
                compareTemplate(handle, prefix, institutionComponent.getSubtitleTemplate(), activityCode);
                compareTemplate(handle, prefix, institutionComponent.getAddButtonTemplate(), activityCode);
                break;
            default:
                throw new DDPException("Unhandled component type: " + block.getComponentType());
        }
    }

    public void traverseQuestion(Handle handle, QuestionDef question, String activityCode) {
        String prefix = String.format("question %s", question.getStableId());

        compareTemplate(handle, prefix, question.getPromptTemplate(), activityCode);
        compareTemplate(handle, prefix, question.getTooltipTemplate(), activityCode);
        compareTemplate(handle, prefix, question.getAdditionalInfoHeaderTemplate(), activityCode);
        compareTemplate(handle, prefix, question.getAdditionalInfoFooterTemplate(), activityCode);

        switch (question.getQuestionType()) {
            case AGREEMENT:
                // Nothing else to do.
                break;
            case FILE:
                // Nothing else to do.
                break;
            case BOOLEAN:
                BoolQuestionDef boolQuestion = (BoolQuestionDef) question;
                compareTemplate(handle, prefix, boolQuestion.getTrueTemplate(), activityCode);
                compareTemplate(handle, prefix, boolQuestion.getFalseTemplate(), activityCode);
                break;
            case DATE:
                DateQuestionDef dateQuestion = (DateQuestionDef) question;
                compareTemplate(handle, prefix, dateQuestion.getPlaceholderTemplate(), activityCode);
                break;
            case NUMERIC:
                NumericQuestionDef numericQuestion = (NumericQuestionDef) question;
                compareTemplate(handle, prefix, numericQuestion.getPlaceholderTemplate(), activityCode);
                break;
            case DECIMAL:
                DecimalQuestionDef decimalQuestion = (DecimalQuestionDef) question;
                compareTemplate(handle, prefix, decimalQuestion.getPlaceholderTemplate(), activityCode);
                break;
            case EQUATION:
                EquationQuestionDef equationQuestion = (EquationQuestionDef) question;
                compareTemplate(handle, prefix, equationQuestion.getPlaceholderTemplate(), activityCode);
                break;
            case PICKLIST:
                traversePicklistQuestion(handle, (PicklistQuestionDef) question, activityCode);
                break;
            case TEXT:
                TextQuestionDef textQuestion = (TextQuestionDef) question;
                compareTemplate(handle, prefix, textQuestion.getPlaceholderTemplate(), activityCode);
                compareTemplate(handle, prefix, textQuestion.getConfirmPromptTemplate(), activityCode);
                compareTemplate(handle, prefix, textQuestion.getConfirmPlaceholderTemplate(), activityCode);
                compareTemplate(handle, prefix, textQuestion.getMismatchMessageTemplate(), activityCode);
                break;
            case COMPOSITE:
                traverseCompositeQuestion(handle, (CompositeQuestionDef) question, activityCode);
                break;
            default:
                throw new DDPException("Unhandled question type: " + question.getQuestionType());
        }

        traverseQuestionValidations(handle, question, activityCode);
    }

    private void traversePicklistQuestion(Handle handle, PicklistQuestionDef question, String activityCode) {
        String questionStableId = question.getStableId();
        String prefix = String.format("question %s", questionStableId);
        compareTemplate(handle, prefix, question.getPicklistLabelTemplate(), activityCode);
        List<PicklistGroupDef> groups = question.getGroups();

        for (int i = 0; i < groups.size(); i++) {
            PicklistGroupDef group = groups.get(i);
            prefix = String.format("question %s group %d", questionStableId, i + 1);
            compareTemplate(handle, prefix, group.getNameTemplate(), activityCode);
        }

        Map<String, PicklistOptionDef> allOptions = question.getAllPicklistOptions().stream()
                .collect(Collectors.toMap(PicklistOptionDef::getStableId, Function.identity()));

        List<String> stableIds = allOptions.keySet().stream().sorted().collect(Collectors.toList());
        for (var stableId : stableIds) {
            PicklistOptionDef option = allOptions.get(stableId);
            prefix = String.format("question %s option %s", questionStableId, stableId);
            compareTemplate(handle, prefix, option.getOptionLabelTemplate(), activityCode);
            compareTemplate(handle, prefix, option.getDetailLabelTemplate(), activityCode);
            compareTemplate(handle, prefix, option.getTooltipTemplate(), activityCode);
        }
    }

    private void traverseCompositeQuestion(Handle handle, CompositeQuestionDef question, String activityCode) {
        String prefix = String.format("question %s", question.getStableId());
        compareTemplate(handle, prefix, question.getAddButtonTemplate(), activityCode);
        compareTemplate(handle, prefix, question.getAdditionalItemTemplate(), activityCode);
        List<QuestionDef> children = question.getChildren();
        for (int i = 0; i < children.size(); i++) {
            traverseQuestion(handle, children.get(i), activityCode);
        }
    }

    private void traverseQuestionValidations(Handle handle, QuestionDef question, String activityCode) {
        String questionStableId = question.getStableId();
        List<RuleDef> rules = question.getValidations();
        for (int i = 0; i < rules.size(); i++) {
            RuleDef rule = rules.get(i);
            String prefix = String.format("question %s rule %s", questionStableId, rule.getRuleType().name());
            compareTemplate(handle, prefix, rule.getHintTemplate(), activityCode);
        }
    }

    private void compareTemplate(Handle handle, String tag, Template current, String activityCode) {
        if (current == null) {
            //log.warn("passed null template ... ignore....");
            return;
        }
        long revisionId = current.getRevisionId().get();
        if (current.getVariables() == null || current.getVariables().isEmpty()) {
            //manual check in logs to alert if any missed
            log.warn("NO TEMPLATE VARS: {} ", current.getTemplateText());
        }
        for (var currentVar : current.getVariables()) {
            compareVariable(handle, tag, revisionId, currentVar, activityCode);
        }
    }

    private void compareVariable(Handle handle, String tag, long revisionId, TemplateVariable current, String activityCode) {

        var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);
        String variableName = current.getName();
        long variableId = current.getId().get();
        if (variableName.equalsIgnoreCase("prompt") && activityCode.equalsIgnoreCase("LOVEDONE")) {
            //skip
            return;
        }

        //todo: handle all diff languages.. for now concentrate on adding es
        var enTranslation = current.getTranslation("en").get();
        var esTranslationOpt = current.getTranslation("es");
        String enText = enTranslation.getText();
        if (allActTransMapEN.get(activityCode) == null) {
            allActTransMapEN.put(activityCode, new TreeMap());
        }
        if (allActTransMapEN.get(activityCode).get(variableName) != null && !variableName.contains("announcement")) {
            //check and handle these manually or in code as special case
            log.warn("**SAME Variable Name found in activity : {} ..variableName: {},  first text: {} .. newText: {} ",
                    activityCode, variableName, allActTransMapEN.get(activityCode).get(variableName), enText);
        }
        allActTransMapEN.get(activityCode).put(variableName, enTranslation.getText());

        if (allActTransMapES.get(activityCode) == null) {
            allActTransMapES.put(activityCode, new TreeMap());
        }

        //tailored to Osteo for now because es.json element names don't match with activity codes
        String key = activityCode.toLowerCase() + "." + variableName;
        if (activityCode.equalsIgnoreCase("CONSENT_ASSENT")
                || activityCode.equalsIgnoreCase("PARENTAL_CONSENT")) {
            key = "parental." + variableName;
        }
        if (activityCode.equalsIgnoreCase("GERMLINE_CONSENT_ADDENDUM")
                || activityCode.equalsIgnoreCase("CONSENT_ADDENDUM")) {
            key = "somatic_consent_addendum." + variableName;
            //special case to handle same variable name in same activity
            if (activityCode.equalsIgnoreCase("GERMLINE_CONSENT_ADDENDUM")
                    && variableName.equalsIgnoreCase("somatic_consent_addendum_election_agree_pediatric")
                    && allActTransMapES.get(activityCode).get(variableName) == null) {
                //set key
                key = "somatic_consent_addendum.somatic_consent_addendum_election_agree1";
            }

        }
        if (activityCode.equalsIgnoreCase("GERMLINE_CONSENT_ADDENDUM_PEDIATRIC")
                || activityCode.equalsIgnoreCase("CONSENT_ADDENDUM_PEDIATRIC")) {
            key = "somatic_consent_addendum_pediatric." + variableName;
        }
        if (activityCode.equalsIgnoreCase("ABOUTYOU")) {
            key = "about_you." + variableName;
        }
        if (activityCode.equalsIgnoreCase("ABOUTCHILD")) {
            key = "about_child." + variableName;
        }
        if (variableName.startsWith("COUNTRY_")) {
            key = "country." + variableName;
        }
        if (variableName.startsWith("STATE_")) {
            key = "state." + variableName;
        }
        if (variableName.startsWith("PROVINCE_")) {
            key = "province." + variableName;
        }
        if (variableName.startsWith("c_") || variableName.startsWith("cancer_")) {
            key = "cancer." + variableName;
        }
        if (activityCode.equalsIgnoreCase("PREQUAL")) {
            //handle same varName for both self and child prompt_COUNTRY
            if (variableName.equalsIgnoreCase("prompt_COUNTRY") && tag.contains("SELF_COUNTRY")) {
                key = "prequal.location_prompt_self";
            }
            if (variableName.equalsIgnoreCase("prompt_COUNTRY") && tag.contains("CHILD_COUNTRY")) {
                key = "prequal.location_prompt_child";
            }
        }

        if (!i18nCfgEs.hasPath(key)) {
            missingTransVars.put(key, enTranslation.getText());
            log.warn("Missing translation for var: {} in ES.JSON with lookup key: {} ", variableName, key);
            return;
        }

        String latestText = i18nCfgEs.getString(key);
        if (esTranslationOpt.isEmpty()) {
            //insert new
            jdbiVariableSubstitution.insert("es", latestText, revisionId, variableId);
            log.info("NEW: [{}] variable {} language {}: inserted substitution : {} ", tag, variableName, "es", latestText);
            allActTransMapES.get(activityCode).put(variableName, latestText);
        } else {
            //compare and update translation value
            Translation translation = esTranslationOpt.get();
            String currentText = translation.getText();
            if (!currentText.equals(latestText)) {
                if (latestText == null || latestText.isBlank()) {
                    log.warn("EMPTY new text for variable: {} . ignored", variableName);
                    return;
                }
                if (variablesToSkip != null && variablesToSkip.contains(variableName)) {
                    log.info("SKIPPED new text for variable: {}", variableName);
                    return;
                }

                boolean updated = jdbiVariableSubstitution.update(
                        translation.getId().get(),
                        translation.getRevisionId().get(),
                        translation.getLanguageCode(),
                        latestText);
                if (updated) {
                    log.info("[{}] variable {} language {}: updated substitution. \ncurrent text: {}  \nlatest  text: {}",
                            tag, variableName, translation.getLanguageCode(), currentText, latestText);
                } else {
                    throw new DDPException(String.format(
                            "Could not update substitution for %s variable %s language %s",
                            tag, variableName, translation.getLanguageCode()));
                }
            }
            allActTransMapES.get(activityCode).put(variableName, latestText);
        }
        //check for ddp.isGoverned mismatch !
        if (enText.contains("ddp.isGovernedParticipant") && !latestText.contains("$ddp.isGovernedParticipant")) {
            //manual check in logs to alert if any missed
            log.warn("MISMATCH: activity: {} .. var: {} ..enText: {} and key: {} .. latestText: {}  ", activityCode,
                    variableName, enText, key, latestText);
        }
    }

    //todo
    //method to search/replace template variable value
    //and revision the related template if needed

}
