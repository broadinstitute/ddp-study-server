package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class CircadiaPrequalV2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(CircadiaPrequalV2.class);
    private static final String PREQUAL_V2_FILE = "prequal-v2.conf";
    private static final String STUDY_GUID = "circadia";
    private static final String V1_VERSION_TAG = "v1";

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
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, adminUser.getId());

        var activityDao = handle.attach(ActivityDao.class);
        var sectionBlockDao = handle.attach(SectionBlockDao.class);

        //
        // Load v2 definition.
        //

        Config v2Cfg = activityBuilder.readDefinitionConfig(PREQUAL_V2_FILE);
        var v2Def = (FormActivityDef) gson.fromJson(ConfigUtil.toJson(v2Cfg), ActivityDef.class);
        activityBuilder.validateDefinition(v2Def);
        LOG.info("Loaded activity definition from file: {}", PREQUAL_V2_FILE);

        //
        // Create version 2.
        //

        String activityCode = v2Cfg.getString("activityCode");
        String v2VersionTag = v2Cfg.getString("versionTag");
        LOG.info("Creating version {} of {}...", v2VersionTag, activityCode);

        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY_GUID, activityCode).get();
        long activityId = activityDto.getActivityId();

        String reason = String.format("Revision activity with studyGuid=%s activityCode=%s versionTag=%s",
                studyDto.getGuid(), activityCode, v2VersionTag);
        var metadata = RevisionMetadata.now(adminUser.getId(), reason);
        ActivityVersionDto v2Dto = activityDao.changeVersion(activityId, v2VersionTag, metadata);
        long revisionId = v2Dto.getRevId();
        LOG.info("Version {} is created with versionId={}, revisionId={}", v2VersionTag, v2Dto.getId(), revisionId);

        ActivityVersionDto v1Dto = handle.attach(JdbiActivityVersion.class)
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, V1_VERSION_TAG)
                .orElseThrow(() -> new DDPException("Could not find version " + V1_VERSION_TAG));
        long v1TerminatedRevId = v1Dto.getRevId(); // v1 should be terminated already after adding v2 above.
        LOG.info("Version {} is terminated with revisionId={}", V1_VERSION_TAG, v1TerminatedRevId);

        //
        // Add new question blocks to v2.
        //

        LOG.info("Starting inserting new question blocks...");
        FormActivityDef currentDef = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, v2Dto);
        FormSectionDef currentSectionDef = currentDef.getSections().get(0);

        List<FormBlockDef> blocks = v2Def.getSections().get(0).getBlocks();

        // add question with id STUDY_REQUIREMENTS

        var block = blocks.get(12);
        var stableId = block.getQuestions().findFirst().get().getStableId();
        int newDisplayOrder = handle.attach(JdbiFormSectionBlock.class)
                .getOrderedActiveMemberships(currentSectionDef.getSectionId())
                .stream()
                .mapToInt(SectionBlockMembershipDto::getDisplayOrder)
                .max()
                .getAsInt() + SectionBlockDao.DISPLAY_ORDER_GAP;
        sectionBlockDao.insertBlockForSection(activityId, currentSectionDef.getSectionId(), newDisplayOrder, block, revisionId);
        LOG.info("Inserted new {} block with id={}, stableId={}, displayOrder={} for activityCode={}, sectionId={}",
                block.getBlockType(), block.getBlockId(), stableId, newDisplayOrder, activityCode, currentSectionDef.getSectionId());

    }

}
