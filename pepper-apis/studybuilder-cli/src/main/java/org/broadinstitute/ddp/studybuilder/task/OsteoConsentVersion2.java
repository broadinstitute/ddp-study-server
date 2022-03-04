package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.*;
import org.broadinstitute.ddp.db.dto.*;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class OsteoConsentVersion2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(OsteoConsentVersion2.class);
    private static final String DATA_FILE = "patches/consent-version-2.conf";
    private static final String OSTEO_STUDY = "CMI-OSTEO";

    private Path cfgPath;
    private Config cfg;
    private Config dataCfg;
    private Instant timestamp;
    private String versionTag;
    private Gson gson;

    private static String SUB_TITLE_V2="RESEARCH ASSENT FORM(Osteosarcoma)";

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);

        if (!studyCfg.getString("study.guid").equals(OSTEO_STUDY)) {
            throw new DDPException("This task is only for the " + OSTEO_STUDY + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.parse(dataCfg.getString("timestamp"));
        this.cfgPath = cfgPath;
        gson =  GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        LanguageStore.init(handle);
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        var activityDao = handle.attach(ActivityDao.class);
        String activityCode = dataCfg.getString("activityCode");
        String studyGuid = studyDto.getGuid();
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .orElseThrow(() -> new DDPException(
                        "Could not find activity for activity code " + activityCode + " and study id " + studyGuid));
        long activityId = activityDto.getActivityId();

        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyGuid, activityCode, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), reason);
        ActivityVersionDto version2 = activityDao.changeVersion(activityId, versionTag, meta);
        addNewBlock(activityId, dataCfg, handle, meta, version2);
        updateTitleAndName(handle, activityDto.getActivityId());
        updateSubTitleOfOsConsent(handle, meta, version2);
    }

    private void updateTitleAndName(Handle handle, long activityId){
        //update name
        int thisRowCount = handle.attach(SqlHelper.class).update18nActivityName(activityId, "Research Assent Form Osteosarcoma Project");
        if (thisRowCount != 1) {
            throw new RuntimeException("Expecting to update 1 Brain i18n activity row, got :" + thisRowCount
                    + "  aborting patch ");
        }

    }

    private void addNewBlock(long activityId, Config config,
                             Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        Config blockConfig = config.getConfig("our_parent_block");
        System.out.println("done 1");
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);

        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(OSTEO_STUDY, "CONSENT_ASSENT").get();
        System.out.println("done 2");
//        ActivityVersionDto ver = handle.attach(JdbiActivityVersion.class).getActiveVersion(activityId).get();

        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, version2);
        System.out.println("number of sections is: "+ currentDef.getSections().size());
        System.out.println("Block config is: " + blockConfig);
        FormSectionDef currentSectionDef = currentDef.getSections().get(3);
        var l = currentSectionDef.getBlocks();
        var block = l.get(l.size()-2);
        block.getQuestions().forEach(e-> System.out.println(e.getPromptTemplate().getTemplateText()));
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(blockConfig), FormBlockDef.class);
        System.out.println("done 3");
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        System.out.println("done 4");

        sectionBlockDao.insertBlockForSection(activityId, currentSectionDef.getSectionId(),
                4, blockDef, version2.getId());
        System.out.println("done 5");
    }


    private void updateSubTitleOfOsConsent(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto){
        String bodyTemplateText = "<h3 class=\"underline\">$osteo_consent_assent_s4_heading</h3>";

        JdbiBlockContent jdbiBlockContent = handle.attach(JdbiBlockContent.class);
        BlockContentDto contentBlock = handle.attach(SqlHelper.class)
                .findContentBlockByBodyText(versionDto.getActivityId(), bodyTemplateText);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newRevId = jdbiRevision.copyAndTerminate(contentBlock.getRevisionId(), meta);
        int numUpdated = jdbiBlockContent.updateRevisionById(contentBlock.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DDPException(String.format(
                    "Unable to terminate active block_content with id=%d, blockId=%d, bodyTemplateId=%d, bodyTemplateText=%s",
                    contentBlock.getId(), contentBlock.getBlockId(), contentBlock.getBodyTemplateId(), bodyTemplateText));
        }

        TemplateDao templateDao = handle.attach(TemplateDao.class);
        templateDao.disableTemplate(contentBlock.getBodyTemplateId(), meta);
        handle.attach(SqlHelper.class)._updateTemplateVarValueByTemplateId(   contentBlock.getBodyTemplateId(), SUB_TITLE_V2);
    }
//
//    private void revisionContentBlock(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto) {
//        String bodyTemplateText = "<h3 class=\"underline\">$osteo_consent_assent_s4_heading</h3>";
//        String newName = "osteo_v2_consent_assent_s4_heading";
//
//        Template newTemplate = Template.html("$"+newName);
//        String newTemplateText = dataCfg.getString("osteo_consent_new_s4_heading");
//        newTemplate.addVariable(TemplateVariable.single(newName, "en", newTemplateText));
//
//        JdbiBlockContent jdbiBlockContent = handle.attach(JdbiBlockContent.class);
//        BlockContentDto contentBlock = handle.attach(SqlHelper.class)
//                .findContentBlockByBodyText(versionDto.getActivityId(), bodyTemplateText);
//
//        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
//        long newRevId = jdbiRevision.copyAndTerminate(contentBlock.getRevisionId(), meta);
//        int numUpdated = jdbiBlockContent.updateRevisionById(contentBlock.getId(), newRevId);
//        if (numUpdated != 1) {
//            throw new DDPException(String.format(
//                    "Unable to terminate active block_content with id=%d, blockId=%d, bodyTemplateId=%d, bodyTemplateText=%s",
//                    contentBlock.getId(), contentBlock.getBlockId(), contentBlock.getBodyTemplateId(), bodyTemplateText));
//        }
//
//        TemplateDao templateDao = handle.attach(TemplateDao.class);
//        templateDao.disableTemplate(contentBlock.getBodyTemplateId(), meta);
//        long newTemplateId = templateDao.insertTemplate(newTemplate, versionDto.getRevId());
//        long newBlockContentId = jdbiBlockContent.insert(contentBlock.getBlockId(), newTemplateId,
//                contentBlock.getTitleTemplateId(), versionDto.getRevId());
//
//        LOG.info("Created block_content with id={}, blockId={}, bodyTemplateId={} for bodyTemplateText={}",
//                newBlockContentId, contentBlock.getBlockId(), newTemplateId, bodyTemplateText);
//
//    }

    private interface SqlHelper extends SqlObject {
        /**
         * Find the content block that has the given body template text. Make sure it is from a block that belongs in the expected activity
         * (and thus the expected study). This is done using a `union` subquery to find all the top-level and nested block ids for the
         * activity and using that to match on the content block.
         */
        @SqlQuery("select bt.* from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
                + " where tmpl.template_text like :text"
                + "   and bt.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId)")
        @RegisterConstructorMapper(BlockContentDto.class)
        BlockContentDto findContentBlockByBodyText(@Bind("activityId") long activityId, @Bind("text") String bodyTemplateText);

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);

        @SqlUpdate("update i18n_template_substitution as i18n"
                + "   join template_variable as var on var.template_variable_id = i18n.template_variable_id"
                + "    set i18n.substitution_value = :value"
                + "  where var.template_id = :templateId")
        int _updateTemplateVarValueByTemplateId(@Bind("templateId") long templateId, @Bind("value") String value);

        @SqlUpdate("update i18n_activity_detail set name = :name where study_activity_id = :studyActivityId")
        int update18nActivityName(@Bind("studyActivityId") long studyActivityId, @Bind("name") String name);

        @SqlQuery("select tv.template_variable_id "
                + "from template_variable as tv "
                + "join i18n_template_substitution as ts on tv.template_variable_id = ts.template_variable_id "
                + "where tv.variable_name = :value and ts.revision_id = :revision")
        long findTemplateVariableId(@Bind("value") String templateVar, @Bind("revision") long revisionId);


    }
}

