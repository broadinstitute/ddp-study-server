package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.*;
import org.broadinstitute.ddp.db.dto.*;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
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

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class OsteoMedicalReleaseUpdate implements CustomTask {


    private static final Logger LOG = LoggerFactory.getLogger(OsteoMedicalReleaseUpdate.class);
    private static final String MEDICAL_RELEASE_MINOR_V2 = "patches/medical-release-minor-v2.conf";
    private static final String MEDICAL_RELEASE_MINOR_V1 = "medical-release-minor.conf";
    private static final String MEDICAL_RELEASE_SELF = "patches/medical-release-self-v2.conf";
    private static final String EN_V2 = "patches/en-v2.conf";


    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Config enCfg;
    private Config minorCfg;
    private Config selfCfg;
    private Config oldMinorCfg;
    private Gson gson;

    private Instant timestamp;
    private String versionTag;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
        this.gson = GsonUtil.standardGson();


        timestamp = Instant.now();

        File enFile = cfgPath.getParent().resolve(EN_V2).toFile();
        if (!enFile.exists()) {
            throw new DDPException("Data file is missing: " + enFile);
        }
        this.enCfg = ConfigFactory.parseFile(enFile).resolveWith(varsCfg);

        File releaseFile = cfgPath.getParent().resolve(MEDICAL_RELEASE_SELF).toFile();
        if (!releaseFile.exists()) {
            throw new DDPException("Data file is missing: " + releaseFile);
        }
        this.selfCfg = ConfigFactory.parseFile(releaseFile).resolveWith(varsCfg);

        File minorFileV2 = cfgPath.getParent().resolve(MEDICAL_RELEASE_MINOR_V2).toFile();
        if (!minorFileV2.exists()) {
            throw new DDPException("Data file is missing: " + minorFileV2);
        }
        this.minorCfg = ConfigFactory.parseFile(minorFileV2).resolveWith(varsCfg);

        versionTag = minorCfg.getString("versionTag");

        File oldMinorFile = cfgPath.getParent().resolve(MEDICAL_RELEASE_MINOR_V1).toFile();
        if (!enFile.exists()) {
            throw new DDPException("Data file is missing: " + enFile);
        }
        this.oldMinorCfg = ConfigFactory.parseFile(oldMinorFile).resolveWith(varsCfg);

    }

    @Override
    public void run(Handle handle) {
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        var adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));
        var jdbiActivity = handle.attach(JdbiActivity.class);
        var sectionBlockDao = handle.attach(SectionBlockDao.class);
        var contentBlockDao = handle.attach(ContentBlockDao.class);

        SqlHelper helper = handle.attach(SqlHelper.class);

        String minorActivityCode = "RELEASE_MINOR";
        String selfActivityCode = "RELEASE_SELF";

        updateTitleAndName(studyDto, jdbiActivity, helper, minorActivityCode, minorCfg);
        updateTitleAndName(studyDto, jdbiActivity, helper, selfActivityCode, selfCfg);

        var introductionUpdate = minorCfg.getConfig("introduction").getConfig("blocks").
                getConfig("updated").getConfig("osteo_release_child_intro");


        updateIntroduction(studyDto, helper, introductionUpdate, minorActivityCode, adminUser, sectionBlockDao, handle, contentBlockDao);

//        String reason = String.format(
//                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
//                studyDto.getGuid(), minorActivityCode, versionTag);
//        RevisionMetadata meta = RevisionMetadata.now(adminUser.getUserId(), reason);


//        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), minorActivityCode);
//        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, meta);


//        updateTemplates(handle, meta, version2);
    }

    // title and name update
    private void updateTitleAndName(StudyDto studyDto, JdbiActivity jdbiActivity,
                                    SqlHelper helper, String releaseActivityCode, Config conf) {

        ActivityDto activityDto = jdbiActivity.findActivityByStudyIdAndCode(studyDto.getId(), releaseActivityCode).get();

        // update title
        String newTitle = conf.getConfig("replaced").getConfigList("translatedTitles").get(0).getString("text");
        helper.update18nActivityTitle(activityDto.getActivityId(), newTitle);

        // update name
        String newName = conf.getConfig("replaced").getConfigList("translatedNames").get(0).getString("text");
        helper.update18nActivityName(activityDto.getActivityId(), newName);
    }

    // update introduction
    private void updateIntroduction(StudyDto studyDto, SqlHelper helper, Config confV2,
                                    String activityCode, UserDto adminUser,
                                    SectionBlockDao sectionBlockDao, Handle handle, ContentBlockDao contentBlockDao) {

        var templateId = helper.selectTemplateId("<p>$osteo_release_child_intro</p>");
        var blockId = helper.selectBlockId(templateId);

        String reason = String.format("Revision activity with studyGuid=%s activityCode=%s versionTag=%s",
                studyDto.getGuid(), activityCode, "v3");
        var metadata = RevisionMetadata.now(adminUser.getUserId(), reason);

        contentBlockDao.disableContentBlock(blockId, metadata);
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

//        ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
//                .getActiveVersion(activityId)
//                .orElseThrow(() -> new DDPException("Could not find version " + "v1"));
        ActivityVersionDto versionDto = handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, metadata);
        long revisionId = versionDto.getRevId();

        long sectionId = helper.selectSectionId(blockId);
        var formBlockDef = gson.fromJson(ConfigUtil.toJson(confV2), FormBlockDef.class);
        var revisionDto = RevisionDto.fromStartMetadata(revisionId, metadata);

        sectionBlockDao.addBlock(activityId, sectionId, 10, formBlockDef, revisionDto);

    }

//    //similar like CircadiaConsentV2
//    private void updateTemplates(Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
//        var config = minorCfg.getConfig("introduction").getConfig("blocks").
//                getConfig("updated").getConfig("osteo_release_child_intro");
//
//        revisionContentBlockTemplate(handle, meta, version2, config);
//
//    }
//
//    //similar like CircadiaConsentV2
//    private void revisionContentBlockTemplate(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto, Config config) {
//
//        ContentBlockDef contentBlockDef = gson.fromJson(ConfigUtil.toJson(config), ContentBlockDef.class);
//        Template newBodyTemplate = contentBlockDef.getBodyTemplate();
//        Template newTitleTemplate = contentBlockDef.getTitleTemplate();
//
//        String oldBlockTemplateText = "osteo_release_child_intro";
//
//        JdbiBlockContent jdbiBlockContent = handle.attach(JdbiBlockContent.class);
//
//        String templateSearchParam = String.format("%s%s%s", "%", oldBlockTemplateText, "%");
//        BlockContentDto contentBlock = handle.attach(SqlHelper.class)
//                .findContentBlockByBodyText(versionDto.getActivityId(), templateSearchParam);
//
//        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
//        long newRevId = jdbiRevision.copyAndTerminate(contentBlock.getRevisionId(), meta);
//        int numUpdated = jdbiBlockContent.updateRevisionById(contentBlock.getId(), newRevId);
//        if (numUpdated != 1) {
//            throw new DDPException(String.format(
//                    "Unable to terminate active block_content with id=%d, blockId=%d, bodyTemplateId=%d",
//                    contentBlock.getId(), contentBlock.getBlockId(), contentBlock.getBodyTemplateId()));
//        }
//
//        TemplateDao templateDao = handle.attach(TemplateDao.class);
//        templateDao.disableTemplate(contentBlock.getBodyTemplateId(), meta);
//        if (contentBlock.getTitleTemplateId() != null) {
//            templateDao.disableTemplate(contentBlock.getTitleTemplateId(), meta);
//        }
//        Long newBodyTemplateId = templateDao.insertTemplate(newBodyTemplate, versionDto.getRevId());
//
//        Long newTitleTemplateId = null;
//        if (newTitleTemplate != null) {
//            newTitleTemplateId = templateDao.insertTemplate(newTitleTemplate, versionDto.getRevId());
//        }
//        long newBlockContentId = jdbiBlockContent.insert(contentBlock.getBlockId(), newBodyTemplateId,
//                newTitleTemplateId, versionDto.getRevId());
//
//        LOG.info("Created block_content with id={}, blockId={}, bodyTemplateId={} for bodyTemplateText={}",
//                newBlockContentId, contentBlock.getBlockId(), newBodyTemplateId, contentBlockDef.getBodyTemplate().getTemplateText());
//    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update i18n_activity_detail set title = :text where study_activity_id = :studyActivityId")
        int update18nActivityTitle(@Bind("studyActivityId") long studyActivityId, @Bind("text") String text);

        @SqlUpdate("update i18n_activity_detail set name = :text where study_activity_id = :studyActivityId")
        int update18nActivityName(@Bind("studyActivityId") long studyActivityId, @Bind("text") String text);

        @SqlQuery("select template_id from template " +
                "where template_text = :templateText")
        long selectTemplateId(@Bind("templateText") String templateText);

        @SqlQuery("select block_id from block_content " +
                "where body_template_id = :body_template_id")
        long selectBlockId(@Bind("body_template_id") long body_template_id);

        @SqlQuery("select form_section_id from form_section__block " +
                "where block_id = :block_id")
        long selectSectionId(@Bind("block_id") long block_id);

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

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);

    }
}



