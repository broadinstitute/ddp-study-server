package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.JdbiFormActivitySetting;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.FormActivitySettingDto;
import org.broadinstitute.ddp.db.dto.FormSectionMembershipDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestBostonConsentV2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(TestBostonConsentV2.class);
    private static final String CONSENT_V2_FILE = "consent-v2.conf";
    private static final String STUDY_GUID = "testboston";
    private static final String V1_VERSION_TAG = "v1";
    private static final int V1_NUM_SECTIONS = 4;

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
        this.gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, adminUser.getId());

        var activityDao = handle.attach(ActivityDao.class);
        var sectionBlockDao = handle.attach(SectionBlockDao.class);
        var templateDao = handle.attach(TemplateDao.class);
        var jdbiActVersion = handle.attach(JdbiActivityVersion.class);
        var jdbiActSettings = handle.attach(JdbiFormActivitySetting.class);
        var jdbiActSection = handle.attach(JdbiFormActivityFormSection.class);
        var jdbiSectionBlock = handle.attach(JdbiFormSectionBlock.class);

        //
        // Load v2 definition.
        //

        Config v2Cfg = activityBuilder.readDefinitionConfig(CONSENT_V2_FILE);
        var v2Def = (ConsentActivityDef) gson.fromJson(ConfigUtil.toJson(v2Cfg), ActivityDef.class);
        activityBuilder.validateDefinition(v2Def);
        LOG.info("Loaded activity definition from: {}", CONSENT_V2_FILE);

        // Extract the new content blocks and build a section object.
        var contentBlocks = new ArrayList<Map<String, Object>>();
        for (var blockCfg : v2Cfg.getConfigList("sections").get(0).getConfigList("contentBlocks")) {
            contentBlocks.add(blockCfg.root().unwrapped());
        }
        var contentBlocksList = ConfigValueFactory.fromIterable(contentBlocks);
        var sectionCfg = ConfigFactory.empty().withValue("blocks", contentBlocksList);
        var singlePageSection = gson.fromJson(ConfigUtil.toJson(sectionCfg), FormSectionDef.class);

        //
        // Create version 2.
        //

        String activityCode = v2Cfg.getString("activityCode");
        String v2VersionTag = v2Cfg.getString("versionTag");
        LOG.info("Creating version {} of {}...", v2VersionTag, activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        String reason = String.format("Revision activity with studyGuid=%s activityCode=%s versionTag=%s",
                studyDto.getGuid(), activityCode, v2VersionTag);
        var metadata = RevisionMetadata.now(adminUser.getId(), reason);
        ActivityVersionDto v2Dto = activityDao.changeVersion(activityId, v2VersionTag, metadata);
        LOG.info("Version {} is created with versionId={}, revisionId={}", v2VersionTag, v2Dto.getId(), v2Dto.getRevId());

        ActivityVersionDto v1Dto = jdbiActVersion
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, V1_VERSION_TAG)
                .orElseThrow(() -> new DDPException("Could not find version 1"));
        long v1TerminatedRevId = v1Dto.getRevId(); // v1 should be terminated already after adding v2 above.
        LOG.info("Version {} is terminated with revisionId={}", V1_VERSION_TAG, v1TerminatedRevId);

        //
        // Disable form settings for v1.
        //

        LOG.info("{}: disabling activity settings...", V1_VERSION_TAG);
        FormActivitySettingDto v1Settings = jdbiActSettings
                .findSettingDtoByActivityIdAndTimestamp(activityId, v1Dto.getRevStart())
                .orElseThrow(() -> new DDPException("Could not find version 1 settings"));
        DBUtils.checkUpdate(1, jdbiActSettings.updateRevisionIdById(v1Settings.getId(), v1TerminatedRevId));

        //
        // Disable all the sections of v1.
        //

        LOG.info("{}: disabling {} sections...", V1_VERSION_TAG, V1_NUM_SECTIONS);
        List<FormSectionMembershipDto> v1SectionMemberships = jdbiActSection
                .findOrderedSectionMemberships(activityId, v1Dto.getRevStart());
        if (v1SectionMemberships.size() != V1_NUM_SECTIONS) {
            throw new DDPException("There should be " + V1_NUM_SECTIONS + " sections but got " + v1SectionMemberships.size());
        }

        // Extract the block ids of signature section so we can add it to the single-page section later.
        long signatureSectionId = v1SectionMemberships.get(V1_NUM_SECTIONS - 1).getSectionId();
        List<Long> signatureBlockIds = jdbiSectionBlock
                .getOrderedActiveMemberships(signatureSectionId).stream()
                .map(SectionBlockMembershipDto::getBlockId)
                .collect(Collectors.toList());

        List<Long> membershipIdsToDisable = new ArrayList<>();
        List<Long> sectionTerminatedRevIds = new ArrayList<>();
        for (var v1SectionMembership : v1SectionMemberships) {
            membershipIdsToDisable.add(v1SectionMembership.getId());
            sectionTerminatedRevIds.add(v1TerminatedRevId);
        }
        int[] updated = jdbiActSection.bulkUpdateRevisionIdsByIds(membershipIdsToDisable, sectionTerminatedRevIds);
        DBUtils.checkUpdate(V1_NUM_SECTIONS, Arrays.stream(updated).sum());

        //
        // Add the new intro and the single-page section of v2.
        //

        LOG.info("{}: adding introduction section...", v2VersionTag);
        FormSectionDef introSection = v2Def.getIntroduction();
        sectionBlockDao.insertSection(activityId, introSection, v2Dto.getRevId());
        LOG.info("Added introduction section with sectionId={}", introSection.getSectionId());

        LOG.info("{}: adding single-page section...", v2VersionTag);
        int displayOrder = SectionBlockDao.DISPLAY_ORDER_GAP;
        sectionBlockDao.insertSection(activityId, singlePageSection, v2Dto.getRevId());
        jdbiActSection.insert(activityId, singlePageSection.getSectionId(), v2Dto.getRevId(), displayOrder);
        LOG.info("Added single-page section with sectionId={}, displayOrder={}", singlePageSection.getSectionId(), displayOrder);

        // Figure out the display order to use for the signature blocks so they come at the end.
        List<Integer> blockOrders = jdbiSectionBlock
                .getOrderedActiveMemberships(singlePageSection.getSectionId())
                .stream()
                .map(SectionBlockMembershipDto::getDisplayOrder)
                .collect(Collectors.toList());
        displayOrder = blockOrders.get(blockOrders.size() - 1);

        LOG.info("{}: adding {} signature blocks to single-page section...", v2VersionTag, signatureBlockIds.size());
        for (var signatureBlockId : signatureBlockIds) {
            displayOrder += SectionBlockDao.DISPLAY_ORDER_GAP;
            jdbiSectionBlock.insert(singlePageSection.getSectionId(), signatureBlockId, displayOrder, v2Dto.getRevId());
        }

        //
        // Create v2 form settings with new intro section and last_updated.
        //

        LOG.info("{}: creating new last_updated details...", v2VersionTag);
        LocalDateTime v2LastUpdated = v2Def.getLastUpdated();
        Template v2LastUpdatedTemplate = v2Def.getLastUpdatedTextTemplate();
        templateDao.insertTemplate(v2LastUpdatedTemplate, v2Dto.getRevId());
        LOG.info("Added last_updated template with templateId={}", v2LastUpdatedTemplate.getTemplateId());

        LOG.info("{}: creating new activity settings...", v2VersionTag);
        jdbiActSettings.insert(
                activityId,
                v1Settings.getListStyleHint(),
                introSection.getSectionId(),
                v1Settings.getClosingSectionId(),
                v2Dto.getRevId(),
                v1Settings.getReadonlyHintTemplateId(),
                v2LastUpdated,
                v2LastUpdatedTemplate.getTemplateId(),
                v1Settings.shouldSnapshotSubstitutionsOnSubmit());

        //
        // Lastly, update consent naming details.
        //

        LOG.info("{}: updating activity naming details...", v2VersionTag);
        var task = new UpdateActivityBaseSettings();
        task.init(cfgPath, studyCfg, varsCfg);
        task.compareNamingDetails(handle, v2Cfg, activityId, v1Dto);
    }
}
