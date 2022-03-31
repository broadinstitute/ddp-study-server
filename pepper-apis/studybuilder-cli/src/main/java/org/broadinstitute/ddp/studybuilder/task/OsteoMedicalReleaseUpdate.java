package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.*;
import org.broadinstitute.ddp.db.dto.*;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

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
    private Gson gson;

    private String versionTag;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
        this.gson = GsonUtil.standardGson();

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

    }

    @Override
    public void run(Handle handle) {
        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        var adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));
        var jdbiActivity = handle.attach(JdbiActivity.class);

        SqlHelper helper = handle.attach(SqlHelper.class);

        String minorActivityCode = "RELEASE_MINOR";
        String selfActivityCode = "RELEASE_SELF";

        updateTitleAndName(studyDto, jdbiActivity, helper, minorActivityCode, minorCfg);
        updateTitleAndName(studyDto, jdbiActivity, helper, selfActivityCode, selfCfg);

        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), minorActivityCode, versionTag);
        RevisionMetadata meta = RevisionMetadata.now(adminUser.getUserId(), reason);


        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), minorActivityCode);
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, meta);

        updateIntroduction(handle, meta, version2);

        updateClosing(handle, meta, version2, helper);

        updateSections(handle, meta, version2);

        removeBlocksFromSection(handle, meta);
    }

    private void updateTitleAndName(StudyDto studyDto, JdbiActivity jdbiActivity,
                                    SqlHelper helper, String releaseActivityCode, Config conf) {

        ActivityDto activityDto = jdbiActivity.findActivityByStudyIdAndCode(studyDto.getId(), releaseActivityCode).get();

        String newTitle = conf.getConfig("replaced").getConfigList("translatedTitles").get(0).getString("text");
        helper.update18nActivityTitle(activityDto.getActivityId(), newTitle);

        String newName = conf.getConfig("replaced").getConfigList("translatedNames").get(0).getString("text");
        helper.update18nActivityName(activityDto.getActivityId(), newName);
    }

    private void updateIntroduction(Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        var config = minorCfg.getConfig("introduction").getConfig("blocks")
                .getConfig("updated").getConfig("osteo_release_child_intro");

        revisionContentBlockIntroduction(handle, meta, version2, config);

    }

    private void revisionContentBlockIntroduction(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto, Config config) {

        ContentBlockDef contentBlockDef = gson.fromJson(ConfigUtil.toJson(config), ContentBlockDef.class);
        Template newBodyTemplate = contentBlockDef.getBodyTemplate();
        Template newTitleTemplate = contentBlockDef.getTitleTemplate();

        String oldBlockTemplateText = "<p>$osteo_release_child_intro</p>";

        JdbiBlockContent jdbiBlockContent = handle.attach(JdbiBlockContent.class);

        var helper = handle.attach(SqlHelper.class);
        var templateId = helper.selectTemplateId(oldBlockTemplateText);
        var blockId = helper.selectBlockId(templateId);


        BlockContentDto contentBlock = jdbiBlockContent.findActiveDtoByBlockId(blockId).get();
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newRevId = jdbiRevision.copyAndTerminate(contentBlock.getRevisionId(), meta);
        int numUpdated = jdbiBlockContent.updateRevisionById(contentBlock.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DDPException(String.format(
                    "Unable to terminate active block_content with id=%d, blockId=%d, bodyTemplateId=%d",
                    contentBlock.getId(), contentBlock.getBlockId(), contentBlock.getBodyTemplateId()));
        }

        TemplateDao templateDao = handle.attach(TemplateDao.class);
        templateDao.disableTemplate(contentBlock.getBodyTemplateId(), meta);
        if (contentBlock.getTitleTemplateId() != null) {
            templateDao.disableTemplate(contentBlock.getTitleTemplateId(), meta);
        }
        Long newBodyTemplateId = templateDao.insertTemplate(newBodyTemplate, versionDto.getRevId());

        Long newTitleTemplateId = null;
        if (newTitleTemplate != null) {
            newTitleTemplateId = templateDao.insertTemplate(newTitleTemplate, versionDto.getRevId());
        }
        long newBlockContentId = jdbiBlockContent.insert(contentBlock.getBlockId(), newBodyTemplateId,
                newTitleTemplateId, versionDto.getRevId());

        LOG.info("Created block_content with id={}, blockId={}, bodyTemplateId={} for bodyTemplateText={}",
                newBlockContentId, contentBlock.getBlockId(), newBodyTemplateId, contentBlockDef.getBodyTemplate().getTemplateText());
    }

    private void updateClosing(Handle handle, RevisionMetadata meta,
                               ActivityVersionDto version2, SqlHelper helper) {
        var config = minorCfg.getConfig("closing").getConfig("blocks")
                .getConfigList("new");

        int blockOrder = 30;
        for (Config cfg : config) {
            insertClosingBlocks(handle, meta, version2, cfg, blockOrder, helper);
            blockOrder += 10;
        }
    }

    private void insertClosingBlocks(Handle handle, RevisionMetadata meta, ActivityVersionDto version2,
                                     Config config, int order, SqlHelper helper) {
        FormBlockDef formBlockDef = gson.fromJson(ConfigUtil.toJson(config), FormBlockDef.class);

        long templateId = helper.selectTemplateId("$osteo_release_child_agree");
        long blockId = helper.selectBlockId(templateId);
        long sectionId = helper.selectSectionId(blockId);

        var sectionBlockDao = handle.attach(SectionBlockDao.class);

        RevisionDto revisionDto = RevisionDto.fromStartMetadata(version2.getRevId(), meta);

        sectionBlockDao.addBlock(version2.getActivityId(), sectionId, order, formBlockDef, revisionDto);
    }

    private void updateSections(Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        var config = minorCfg.getConfig("sections").getConfig("0").getConfig("blocks")
                .getConfig("updated").getConfig("1");

        revisionContentBlockSection(handle, meta, version2, config);

    }

    private void revisionContentBlockSection(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto, Config config) {
        var physicianComponentBlockDef = gson.fromJson(ConfigUtil.toJson(config), PhysicianComponentDef.class);

        String oldAddButtonTemplateText = "$osteo_release_child_physician_button";
        String oldTitleTemplateText = "$osteo_release_child_physician_title";
        String oldSubtitleTemplateText = "$osteo_release_child_physician_subtitle";

        var helper = handle.attach(SqlHelper.class);

        var oldAddButtonTemplateTextId = helper.selectTemplateId(oldAddButtonTemplateText);
        var oldTitleTemplateTextId = helper.selectTemplateId(oldTitleTemplateText);
        var oldSubtitleTemplateTextId = helper.selectTemplateId(oldSubtitleTemplateText);

        var templateDao = handle.attach(TemplateDao.class);

        var componentId = helper.selectComponentId(oldTitleTemplateTextId);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newRevId = jdbiRevision.copyAndTerminate(versionDto.getRevId(), meta);

        templateDao.disableTemplate(oldAddButtonTemplateTextId, meta);
        templateDao.disableTemplate(oldSubtitleTemplateTextId, meta);
        templateDao.disableTemplate(oldTitleTemplateTextId, meta);

        var blockId = helper.selectBlockIdFromBlockComponent(componentId);

        var sectionBlock = handle.attach(SectionBlockDao.class);

        //        var contentBlockDao = handle.attach(ContentBlockDao.class);
        //        contentBlockDao.disableContentBlock(blockId, meta);

        var componentDao = handle.attach(ComponentDao.class);

        componentDao.insertComponentDef(blockId, physicianComponentBlockDef, newRevId);
    }

    private void removeBlocksFromSection(Handle handle, RevisionMetadata meta) {
        var config = minorCfg.getConfig("sections").getConfig("0").getConfig("blocks")
                .getConfigList("removed");
        for (Config cfg : config) {
            removeContentFromBlockSection(handle, meta, cfg);
        }

    }


    private void removeContentFromBlockSection(Handle handle, RevisionMetadata meta, Config config) {
        var titleTemplateText = config.getString("titleTemplateText");

        var helper = handle.attach(SqlHelper.class);


        var titleTemplateTextId = helper.selectTemplateId(titleTemplateText);

        long componentId;

        if (config.getString("type").equals("default")) {

            componentId = helper.selectComponentId(titleTemplateTextId);
        } else {
            componentId = helper.selectComponentIdFromMailingAddress(titleTemplateTextId);
        }

        var blockId = helper.selectBlockIdFromBlockComponent(componentId);

        disableTemplate(handle, componentId, meta);
    }

    private void disableTemplate(Handle handle, long componentId, RevisionMetadata meta) {
        JdbiComponent jdbiComponent = handle.attach(JdbiComponent.class);
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        TemplateDao templateDao = handle.attach(TemplateDao.class);

        ComponentDto componentDto = jdbiComponent.findComponentDtosByIds(List.of(componentId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Cannot find active content block with id " + componentId));

        long oldRevId = componentDto.getRevisionId();

        jdbiRevision.tryDeleteOrphanedRevision(oldRevId);
        for (long id : componentDto.getTemplateIds()) {
            templateDao.disableTemplate(id, meta);
        }
    }


    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update i18n_activity_detail set title = :text where study_activity_id = :studyActivityId")
        int update18nActivityTitle(@Bind("studyActivityId") long studyActivityId, @Bind("text") String text);

        @SqlUpdate("update i18n_activity_detail set name = :text where study_activity_id = :studyActivityId")
        int update18nActivityName(@Bind("studyActivityId") long studyActivityId, @Bind("text") String text);

        @SqlQuery("select template_id from template "
                + "where template_text = :templateText")
        long selectTemplateId(@Bind("templateText") String templateText);

        @SqlQuery("select block_id from block_content "
                + "where body_template_id = :bodyTemplateId")
        long selectBlockId(@Bind("bodyTemplateId") long bodyTemplateId);

        @SqlQuery("select form_section_id from form_section__block "
                + "where block_id = :blockId")
        long selectSectionId(@Bind("blockId") long blockId);

        @SqlQuery("select institution_physician_component_id from institution_physician_component "
                + "where title_template_id = :titleTemplateId ")
        long selectComponentId(@Bind("titleTemplateId") long titleTemplateId);

        @SqlQuery("select component_id from mailing_address_component "
                + "where title_template_id = :titleTemplateId ")
        long selectComponentIdFromMailingAddress(@Bind("titleTemplateId") long titleTemplateId);

        @SqlQuery("select block_id from block_component "
                + "where component_id = :componentId")
        long selectBlockIdFromBlockComponent(@Bind("componentId") long componentId);

        @SqlQuery("select revision_id from component "
                + "where component_id = :componentId")
        long selectRevisionIdFromComponent(@Bind("componentId") long componentId);

        @SqlUpdate("update component set revision_id = :revisionId where component_id = :componentId")
        int updateRevisionIdById(@Bind("componentId") long componentId,
                                 @Bind("revisionId") long revisionId);

    }
}



