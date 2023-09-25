package org.broadinstitute.ddp.studybuilder.task;

import com.google.common.base.Functions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiTemplate;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
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
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.broadinstitute.ddp.studybuilder.BuilderUtils.parseAndValidateTemplate;

/**
 * General task to iterate through all templates in a study and update them in-place. For each template, this will
 * compare the template text, the variables, and the variable translations, updating these things in-place without
 * revisioning.
 * Source of truth is DB.
 * Iterate through each translation variable in DB and find matching variable in i198n.es.conf
 * and add/update the translation
 */
@Slf4j
@NoArgsConstructor
public class UpdateTranslationsSourceDB implements CustomTask {
    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private List<String> variablesToSkip;
    private Config i18nCfgEn;
    private Config i18nCfgEs;
    private Map<String, Map> allActTransMapEN;
    private Map<String, Map> allActTransMapES;
    private Map<String, String> missingTransVars = new TreeMap<>();
    private List<String> templateTypes = new ArrayList<>();
    Gson gson = new Gson();
    Type typeObject = new TypeToken<HashMap>() {
    }.getType();


    public UpdateTranslationsSourceDB(List<String> variablesToSkip) {
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
        //todo pass as params
        i18nCfgEn = ConfigFactory.parseFile(Paths.get("studybuilder-cli/studies/osteo/i18n/en.conf").toFile());
        i18nCfgEs = ConfigFactory.parseFile(Paths.get("studybuilder-cli/studies/osteo/i18n/es.conf").toFile());
        allActTransMapEN = new TreeMap<String, Map>();
        allActTransMapES = new TreeMap<String, Map>();

        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        User admin = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, admin.getId());

        //traverseStudySettings(handle, studyDto.getId());
        //traverseKitConfigurations(handle, studyDto.getId());
        traverseEventConfigurations(handle, studyDto.getId());
        traverseActivities(handle, studyDto.getId(), activityBuilder);
        String gsonDataEN = gson.toJson(allActTransMapEN, typeObject);
        log.info("---------EN---------");
        //log.info(gsonDataEN);

        log.info("---------ES---------");
        String gsonDataES = gson.toJson(allActTransMapES, typeObject);
        //log.info(gsonDataES);

    }

    // Note: This currently assumes only ANNOUNCEMENT event actions has templates.
    private void traverseEventConfigurations(Handle handle, long studyId) {
        log.info("Comparing templates in event configurations...");

        var eventDao = handle.attach(EventDao.class);
        var templateDao = handle.attach(TemplateDao.class);

        for (var eventConfig : eventDao.getAllEventConfigurationsByStudyId(studyId)) {
            if (EventActionType.ANNOUNCEMENT.equals(eventConfig.getEventActionType())) {
                long timestamp = Instant.now().toEpochMilli();
                long templateId = ((AnnouncementEventAction) eventConfig.getEventAction()).getMessageTemplateId();
                Template current = templateDao.loadTemplateByIdAndTimestamp(templateId, timestamp);
                String tag = String.format("event announcementMessageTemplate %d", templateId);
                compareTemplate(handle, tag, current, "announcements");
            }
        }
    }

    // Best-effort attempt at making a unique identifier for event configuration.
    // watch out for scenarios where diff events might end up with same unique identifier
    // ex:- trigger and order are same but diff pre/cancel expressions !
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

    //Source of Truth is DB
    //Read DB for activity latest version and get translations from current 18n.<lc>.conf and update in DB
    //Dont look at conf file.. todo try to generate conf file..later
    private void traverseActivities(Handle handle, long studyId, ActivityBuilder activityBuilder) {
        var activityDao = handle.attach(ActivityDao.class);
        var jdbiActivity = handle.attach(JdbiActivity.class);
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);

        //load study config from DB .. all activities latest version
        List<ActivityDto> allActivities = jdbiActivity.findOrderedDtosByStudyId(studyId);
        log.info("Activity count: {} ", allActivities.size());
        for (ActivityDto dto : allActivities) {
            log.debug(" activity : {} .. parent: {} ", dto.getActivityCode(), dto.getParentActivityCode());
        }

        for (ActivityDto activityDto : allActivities) {

            //if (activityDto.getActivityCode().equalsIgnoreCase("prequal")) {

            //load allActivityTransVars .. map of varName , Conf file i18n translationVarName
            //readAllTranslations(activityBuilder);
            //readAllTransVars(activityBuilder);

            //get latest version
            ActivityVersionDto versionDto = jdbiActVersion.getActiveVersion(activityDto.getActivityId()).get();
            FormActivityDef activity = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, versionDto);
            log.info("###### activity : {} .. parent: {}  .. version: {}", activityDto.getActivityCode(),
                    activityDto.getParentActivityCode(), versionDto.getVersionTag());

            if (!activityDto.getActivityCode().startsWith("FAMILY_HISTORY") &&
                    !activityDto.getActivityCode().equalsIgnoreCase("ABOUT_YOU_ACTIVITY") &&
                    !activityDto.getActivityCode().equalsIgnoreCase("SOMATIC_RESULTS")) {
                traverseActivity(handle, activity);
            }

            compareNamingDetails(handle, activity.getActivityCode().toLowerCase(), activity.getActivityId(), versionDto);
            Template hintTemplate = activity.getReadonlyHintTemplate();
            compareTemplate(handle, activity.getTag(), hintTemplate, activity.getActivityCode());
            Template lastUpdatedTemplate = activity.getReadonlyHintTemplate();
            compareTemplate(handle, activity.getTag(), lastUpdatedTemplate, activity.getActivityCode());

            //if (activityDto.getActivityCode().equalsIgnoreCase("ABOUTYOU")) {
                //traverseActivity(handle, activity);
            //}
            log.info("MISSING translation vars in es.conf: {}", activityDto.getActivityCode());
            String gsonDataMiss = gson.toJson(missingTransVars, typeObject);
            log.info(gsonDataMiss);
            //}
        }
    }

    void traverseActivity(Handle handle, FormActivityDef activity) {
        long activityId = activity.getActivityId();

        log.info("Comparing activity {} naming details...", activity.getActivityCode());
        //var task = new UpdateActivityBaseSettings();
        //task.init(cfgPath, studyCfg, varsCfg);
        //compareNamingDetails(handle, activity.getActivityCode(), activityId, activity.);
        //task.compareStatusSummaries(handle, definition, activityId);


        List<FormSectionDef> sections = activity.getAllSections();
        log.info("ACTIVITY: {} .. DB sections: {}  ", activity.getActivityCode(), sections.size());

        for (int i = 0; i < sections.size(); i++) {
            traverseSection(handle, i + 1, sections.get(i), activity.getActivityCode());
        }
    }

    public void compareNamingDetails(Handle handle, String activityCode, long activityId, ActivityVersionDto versionDto) {
        if (activityCode.equalsIgnoreCase("LOVEDONE") || activityCode.equalsIgnoreCase("prequal")) {
            return;
        }
        var activityI18nDao = handle.attach(ActivityI18nDao.class);
        Map<String, ActivityI18nDetail> currentDetails = activityI18nDao
                .findDetailsByActivityIdAndTimestamp(activityId, versionDto.getRevStart())
                .stream()
                .collect(Collectors.toMap(ActivityI18nDetail::getIsoLangCode, Functions.identity()));

        ActivityI18nDetail currentES = currentDetails.get("es");
        ActivityI18nDetail latestDetails =
                buildLatestNamingDetail(activityId, versionDto.getRevId(), activityCode, currentES);
        if (latestDetails == null) {
            return;
        }

        if (currentES == null) {
            //insert new
            List<ActivityI18nDetail> newDetails = Collections.singletonList(latestDetails);
            activityI18nDao.insertDetails(newDetails);
            log.info("NEW: Inserted naming details for activity {} .. language: {}", activityCode, "es");
        } else {
            if (!currentES.equals(latestDetails)) {
                activityI18nDao.updateDetails(Collections.singletonList(latestDetails));
                log.info("Updated naming details for activity {} .. language: {}", activityCode, "es");
            }
        }
    }

    private ActivityI18nDetail buildLatestNamingDetail(long activityId, long revisionId, String activityCode,
                                                                     ActivityI18nDetail current) {

        String key = activityCode;
        if (activityCode.equalsIgnoreCase("CONSENT_ASSENT") || activityCode.equalsIgnoreCase("PARENTAL_CONSENT")) {
            key = "parental";
        }
        if (activityCode.equalsIgnoreCase("GERMLINE_CONSENT_ADDENDUM") ||
                activityCode.equalsIgnoreCase("CONSENT_ADDENDUM")) {
            key = "somatic_consent_addendum";
        }
        if (activityCode.equalsIgnoreCase("GERMLINE_CONSENT_ADDENDUM") ||
                activityCode.equalsIgnoreCase("CONSENT_ADDENDUM")) {
            key = "somatic_consent_addendum";
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
        String subTitle = null;
        if (i18nCfgEs.hasPath(key + "." + "subTitle")) {
            subTitle = i18nCfgEs.getString(key + "." + "subTitle");
        }
        String description = null;
        if (i18nCfgEs.hasPath(key + "." + "subTitle")) {
            description = i18nCfgEs.getString(key + "." + "description");
        }

        ActivityI18nDetail latest = new ActivityI18nDetail(
                activityId,
                "es",
                name,
                secondName,
                title,
                subTitle,
                description,
                current == null ? revisionId : current.getRevisionId());
        return latest;
    }

    public void traverseSection(Handle handle, int sectionNum, FormSectionDef section, String activityCode) {
        String prefix = String.format("section %d", sectionNum);
        compareTemplate(handle, prefix, section.getNameTemplate(), activityCode);

        List<FormBlockDef> blocks = section.getBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            traverseBlockNew(handle, sectionNum, i + 1, null, blocks.get(i), activityCode);
        }
    }


    private void traverseBlockNew(Handle handle, int sectionNum, int blockNum, Integer nestedNum,
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
                    traverseBlockNew(handle, sectionNum, blockNum, i + 1, condNested.get(i), activityCode);
                }

                break;
            case GROUP:
                GroupBlockDef groupBlock = (GroupBlockDef) block;
                String type = groupBlock.getBlockType().name();
                String prefix = String.format("section %d block %d %s", sectionNum, blockNum, type);
                compareTemplate(handle, prefix, groupBlock.getTitleTemplate(), activityCode);

                List<FormBlockDef> groupNested = groupBlock.getNested();

                for (int i = 0; i < groupNested.size(); i++) {
                    traverseBlockNew(handle, sectionNum, blockNum, i + 1, groupNested.get(i), activityCode);
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

    // Note: for now, we're querying the content block templates here.
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

        //todo
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
            //log.warn("--passed null template ... ignore....");
            return;
        }
        //long templateId = current.getTemplateId();
        long revisionId = current.getRevisionId().get();

        //String currentText = current.getTemplateText();
        //String latestText = latest.getTemplateText();

        for (var currentVar : current.getVariables()) {
            compareVariable(handle, tag, revisionId, currentVar, activityCode);
        }
    }

    private void compareVariable(Handle handle, String tag, long revisionId, TemplateVariable current, String activityCode) {

        var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);
        String variableName = current.getName();
        //log.info("Variable Name: {}", variableName);
        long variableId = current.getId().get();

        //todo: handle all diff languages.. for now concentrate on adding es
        var enTranslation = current.getTranslation("en").get();
        var esTranslationOpt = current.getTranslation("es");

        if (allActTransMapEN.get(activityCode) == null) {
            allActTransMapEN.put(activityCode, new TreeMap());
        }
        allActTransMapEN.get(activityCode).put(variableName, enTranslation.getText());


        if (allActTransMapES.get(activityCode) == null) {
            allActTransMapES.put(activityCode, new TreeMap());
        }

        //tailored to Osteo for now
        String key = activityCode.toLowerCase() + "." + variableName;
        if (activityCode.equalsIgnoreCase("CONSENT_ASSENT") || activityCode.equalsIgnoreCase("PARENTAL_CONSENT")) {
            key = "parental." + variableName;
        }
        if (activityCode.equalsIgnoreCase("GERMLINE_CONSENT_ADDENDUM") ||
                activityCode.equalsIgnoreCase("CONSENT_ADDENDUM")) {
            key = "somatic_consent_addendum." + variableName;
        }
        if (activityCode.equalsIgnoreCase("GERMLINE_CONSENT_ADDENDUM") ||
                activityCode.equalsIgnoreCase("CONSENT_ADDENDUM")) {
            key = "somatic_consent_addendum." + variableName;
        }
        if (activityCode.equalsIgnoreCase("ABOUTYOU")) {
            key = "about_you." + variableName;
        }
        if (activityCode.equalsIgnoreCase("ABOUTCHILD")) {
            key = "about_child." + variableName;
        }
        //
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

        if (!i18nCfgEs.hasPath(key)) {
            missingTransVars.put(key, enTranslation.getText());
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
                    allActTransMapES.get(activityCode).put(variableName, latestText);
                } else {
                    throw new DDPException(String.format(
                            "Could not update substitution for %s variable %s language %s",
                            tag, variableName, translation.getLanguageCode()));
                }
            }
        }

    }

}
