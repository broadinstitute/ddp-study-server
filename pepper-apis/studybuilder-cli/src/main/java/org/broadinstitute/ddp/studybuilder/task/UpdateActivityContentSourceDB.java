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
import org.broadinstitute.ddp.db.dao.JdbiActivityValidation;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiBlockContent;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiTemplate;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityValidationDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
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
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.AnnouncementEventAction;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.jdbi.v3.core.Handle;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * General task to iterate through activity content in a study and update needed content from passed patch file.
 * For each activity of the study, iterate through each section, block, each template and for each template, search and
 * compare the template text, the variables, and the variable translations, updating these things in-place with revisioning if needed.
 * Source of truth is DB.
 * Iterate through each translation variable in DB and find matching text and replace
 * Iterate through each template text in DB and find matching text and replace
 */
@Slf4j
@NoArgsConstructor
public class UpdateActivityContentSourceDB implements CustomTask {
    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private List<String> variablesToSkip;
    private Config i18nCfgEn;
    private Config i18nCfgEs;
    private Map<String, Set<TemplateVariable>> interestedTransVarsMapEN;
    private Map<String, Set<Template>> interestedTemplatesMapEN;
    private Map<String, Map> allActTransMapEN;
    private Map<String, Map> allActTransMapES;
    private Map<String, String> missingTransVars = new TreeMap<>();
    private String enFilePath = "studybuilder-cli/studies/pancan/i18n/en.conf";
    private String esFilePath = "studybuilder-cli/studies/pancan/i18n/es.conf";
    private User adminUser;

    private Instant timestamp;
    private Config cfg;
    private ActivityDao activityDao;
    private JdbiTemplate jdbiTemplate;
    private JdbiBlockContent jdbiBlockContent;
    private JdbiVariableSubstitution jdbiVarSubst;
    private JdbiRevision jdbiRevision;
    private TemplateDao templateDao;

    private Set<TemplateUpdateInfo> templateUpdateList;
    private boolean revision = true;

    Gson gson = new Gson();
    Type typeObject = new TypeToken<HashMap>() {
    }.getType();


    public UpdateActivityContentSourceDB(List<String> variablesToSkip) {
        this.variablesToSkip = variablesToSkip;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        cfgPath = cfgPath;
        studyCfg = studyCfg;
        varsCfg = varsCfg;
        timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK:: UpdateActivityContentSourceDB ");

        activityDao = handle.attach(ActivityDao.class);
        jdbiTemplate = handle.attach(JdbiTemplate.class);
        jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        jdbiRevision = handle.attach(JdbiRevision.class);
        templateDao = handle.attach(TemplateDao.class);
        jdbiBlockContent = handle.attach(JdbiBlockContent.class);
        adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();

        //load i18n
        //todo pass file path as params
        //need controls to block running on studies accidentally
        i18nCfgEn = ConfigFactory.parseFile(Paths.get(enFilePath).toFile());
        i18nCfgEs = ConfigFactory.parseFile(Paths.get(esFilePath).toFile());

        templateUpdateList = new HashSet<>();

        //template with multiple substitutions
        //should we group ?
        //todo read the changes from a patch config file
        Set<TemplateUpdateInfo> infos = new HashSet<>();
        infos.add(new TemplateUpdateInfo("Nikhil Wagle, MD", "Diane Diehl, PhD", TemplateActionType.UPDATE));
        infos.add(new TemplateUpdateInfo("Corrie Painter, PhD", "",
                TemplateActionType.UPDATE));

        templateUpdateList.addAll(infos);
        templateUpdateList.add(new TemplateUpdateInfo(
                "Nikhil Wagle, MD, Dana-Farber Cancer Institute, 450 Brookline Ave, Boston, MA, 02215",
                "Diane Diehl, PhD, Count Me In, 415 Main Street, 105B, Cambridge, MA 02142",
                TemplateActionType.UPDATE));

        //Maps to save ALL translations for any verification
        allActTransMapEN = new TreeMap<String, Map>();
        allActTransMapES = new TreeMap<String, Map>();
        interestedTransVarsMapEN = new TreeMap<String, Set<TemplateVariable>>();
        interestedTemplatesMapEN = new TreeMap<String, Set<Template>>();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        User admin = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, admin.getId());

        //traverseEventConfigurations(handle, studyDto.getId());
        traverseActivities(handle, studyDto);
        String gsonDataTemplatesEN = gson.toJson(interestedTemplatesMapEN, typeObject);
        String gsonDataVarsEN = gson.toJson(interestedTransVarsMapEN, typeObject);
        log.info("Reverse engineered EN Matching templates");
        log.info(gsonDataTemplatesEN);
        log.info("Reverse engineered EN Matching template VARS");
        log.info(gsonDataVarsEN);

        //String gsonDataEN = gson.toJson(allActTransMapEN, typeObject);
        //log.info("Reverse engineered EN translation file");
        //log.info(gsonDataEN);

        //log.info("Reverse engineered ES translation file");
        //String gsonDataES = gson.toJson(allActTransMapES, typeObject);
        //log.info(gsonDataES);

    }

    private void revisionTemplate(Template template, RevisionMetadata meta, ActivityVersionDto newVersionDto,
                                  Set<TemplateUpdateInfo> updateInfos) {

        //for now considering as body_template and not title_template
        //todo .. query DB and act accordingly

        //steps: revision the block, revision the template, insert new template, create new block_content

        //iterate through and apply ALL changes
        String newTemplateText = template.getTemplateText();
        log.info("revisioning and updating template : {}", template.getTemplateText());
        for (TemplateUpdateInfo updateInfo : updateInfos) {
            newTemplateText = newTemplateText.replace(updateInfo.getSearchString(), updateInfo.getReplaceString());
            newTemplateText = newTemplateText.replace("<li></li>", ""); //remove empty tags if any
        }
        log.info("updated new template text: {} ", newTemplateText);

        //find the block
        BlockContentDto blockContentDto = jdbiBlockContent.findDtoByBodyTemplateId(template.getTemplateId()).get();
        log.info("blockID: {} .. tileTemplateId: {}", blockContentDto.getBlockId(), blockContentDto.getTitleTemplateId());

        //revision this template
        long newRevId = jdbiRevision.copyAndTerminate(template.getRevisionId().get(), meta);
        jdbiTemplate.updateRevisionIdById(newRevId, template.getTemplateId());
        templateDao.disableTemplate(template.getTemplateId(), meta);

        //revision the block
        long newBlockRevId = jdbiRevision.copyAndTerminate(blockContentDto.getRevisionId(), meta);
        jdbiBlockContent.updateRevisionById(blockContentDto.getId(), newBlockRevId);

        //clone template with new text and insert it, create new block_content
        Template newBodyTemplate = new Template(template.getTemplateType(), template.getTemplateCode(), newTemplateText);
        newBodyTemplate.setVariables(template.getVariables());
        long newTemplateId = templateDao.insertTemplate(newBodyTemplate, newVersionDto.getRevId());
        long newBlockContentId = jdbiBlockContent.insert(blockContentDto.getBlockId(), newTemplateId,
                blockContentDto.getTitleTemplateId(), newVersionDto.getRevId());
        log.info("Created block_content with id={}, blockId={}, bodyTemplateId={} for bodyTemplateText={}",
                newBlockContentId, blockContentDto.getBlockId(), newTemplateId, newTemplateText);
    }

    private void revisionVariableTranslation(TemplateVariable variable,
                                             RevisionMetadata meta, ActivityVersionDto newVersionDto, TemplateUpdateInfo updateInfo) {
        log.info("revisioning and updating template variable: {}", variable.getName());
        Long tmplVarId = variable.getId().get();
        //todo handle scenario where search and replace text is different by language. PI names/address can be same though
        //read from a patch config file whic has replace text by language

        for (Translation currTranslation : variable.getTranslations()) {
            String currentText = currTranslation.getText();
            String newTemplateText = currentText.replace(updateInfo.getSearchString(), updateInfo.getReplaceString());
            //revision this var
            long newRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
            long[] revIds = {newRevId};
            jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
            jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, newVersionDto.getRevId(), variable.getId().get());
            log.info("revisioned and updated template variable: {}", variable.getId().get());
        }
    }

    // Note: This currently assumes only ANNOUNCEMENT event actions has templates.
    private void traverseEventConfigurations(Handle handle, long studyId) {
        log.info("Comparing templates in event configurations...");

        var eventDao = handle.attach(EventDao.class);
        var templateDao = handle.attach(TemplateDao.class);

        //watch out for variables with same name referring to diff activities //todo
        String key = "announcements";
        for (var eventConfig : eventDao.getAllEventConfigurationsByStudyId(studyId)) {
            if (EventActionType.ANNOUNCEMENT.equals(eventConfig.getEventActionType())) {
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
    private void traverseActivities(Handle handle, StudyDto studyDto) {
        var jdbiActivity = handle.attach(JdbiActivity.class);
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);
        var jdbiActivityValidation = handle.attach(JdbiActivityValidation.class);
        var templateDao = handle.attach(TemplateDao.class);

        //load study config from DB. All activities latest version
        List<ActivityDto> allActivities = jdbiActivity.findOrderedDtosByStudyId(studyDto.getId());
        log.info("Activity count: {} ", allActivities.size());
        for (ActivityDto activityDto : allActivities) {
            //get latest version
            ActivityVersionDto versionDto = jdbiActVersion.getActiveVersion(activityDto.getActivityId()).get();
            FormActivityDef activity = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, versionDto);
            log.info("#activity : {}  .. version: {}", activityDto.getActivityCode(), versionDto.getVersionTag());

            traverseActivity(handle, activity);

            //compareNamingDetails(handle, activity.getActivityCode().toLowerCase(), activity.getActivityId(), versionDto);
            //todo handle activity summaries

            Template hintTemplate = activity.getReadonlyHintTemplate();
            //compareTemplate(handle, activity.getTag(), hintTemplate, activity.getActivityCode());
            Template lastUpdatedTemplate = activity.getReadonlyHintTemplate();
            //compareTemplate(handle, activity.getTag(), lastUpdatedTemplate, activity.getActivityCode());

            //handle activity level validations / errorMessages / messageTempates
            List<ActivityValidationDto> validationDtos = jdbiActivityValidation._findByActivityId(activityDto.getActivityId());

            //manual check in logs if any translations are missing  in es.json
            log.info("MISSING translation vars in es.conf: {}", activityDto.getActivityCode());
            String gsonDataMiss = gson.toJson(missingTransVars, typeObject);
            log.info(gsonDataMiss);

            if (revision) {
                Set<Template> tmplSet = interestedTemplatesMapEN.get(activityDto.getActivityCode());
                Set<TemplateVariable> tmplVarsSet = interestedTransVarsMapEN.get(activityDto.getActivityCode());

                if ((tmplSet != null && !tmplSet.isEmpty()) || (tmplVarsSet != null && !tmplVarsSet.isEmpty())) {
                    //revision the activities and do the updates
                    String currVersion = versionDto.getVersionTag().substring(1, 2);
                    String nextVersion = "v" + (Integer.valueOf(currVersion) + 1);
                    String reasonVersion = String.format(
                            "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                            studyDto.getGuid(), activityDto.getActivityCode(), nextVersion);
                    RevisionMetadata metaData = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), reasonVersion);
                    long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityDto.getActivityCode());
                    ActivityVersionDto newVersionDto = activityDao.changeVersion(activityId, nextVersion, metaData);
                    log.info("Revisioned activity: {} to version: {}", activityDto.getActivityCode(), nextVersion);
                    //apply new changes
                    //apply template text changes
                    if (tmplSet != null) {
                        for (Template tmpl : tmplSet) {
                            revisionTemplate(tmpl, metaData, newVersionDto, templateUpdateList);
                        }
                    }

                    //apply template translation variable text changes
                    if (tmplVarsSet != null) {
                        for (TemplateVariable tmplVar : interestedTransVarsMapEN.get(activityDto.getActivityCode())) {
                            //try all possible updates ?
                            for (TemplateUpdateInfo updateInfo : templateUpdateList) {
                                if (tmplVar.getTranslation("en").get().getText().contains(updateInfo.getSearchString())) {
                                    //revision this var
                                    revisionVariableTranslation(tmplVar, metaData, newVersionDto, updateInfo);
                                }
                            }
                        }
                    }
                }
            }
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
        var activityI18nDao = handle.attach(ActivityI18nDao.class);
        Map<String, ActivityI18nDetail> currentDetails = activityI18nDao
                .findDetailsByActivityIdAndTimestamp(activityId, versionDto.getRevStart())
                .stream()
                .collect(Collectors.toMap(ActivityI18nDetail::getIsoLangCode, Functions.identity()));

        ActivityI18nDetail currentES = currentDetails.get("es");
        ActivityI18nDetail latestDetails =
                buildLatestNamingDetail(activityId, versionDto.getRevId(), activityCode, currentES);
        if (latestDetails == null) {
            log.warn("NO Latest Details null for activity: {}", activityCode);
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
            case MATRIX:
                //not supported
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
        for (TemplateUpdateInfo updateInfo : templateUpdateList) {
            if (current.getTemplateText().contains(updateInfo.getSearchString())) {
                if (!interestedTemplatesMapEN.containsKey(activityCode)) {
                    interestedTemplatesMapEN.put(activityCode, new HashSet<>());
                }
                interestedTemplatesMapEN.get(activityCode).add(current);
                log.info("MATCH: template: {} ", current.getTemplateText());
            }
            for (var currentVar : current.getVariables()) {
                boolean isMatch = checkVariable(currentVar, updateInfo.getSearchString());
                if (isMatch) {
                    if (!interestedTransVarsMapEN.containsKey(activityCode)) {
                        interestedTransVarsMapEN.put(activityCode, new HashSet<>());
                    }
                    interestedTransVarsMapEN.get(activityCode).add(currentVar);
                    log.info("MATCH: Template Variable: {} ", currentVar.getName());
                }
                //todo hadle multiple changes.. need to track which change to apply ?
            }
        }
    }

    private boolean checkVariable(TemplateVariable current, String searchText) {
        if (current == null || searchText.isEmpty()) {
            return false;
        }
        //assumption: search string is english
        String currentText = current.getTranslation("en").get().getText();
        if (currentText.contains(searchText)) {
            //ok .. match
            return true;
        } else {
            return false;
        }
    }

}
