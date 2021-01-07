package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiBlockNesting;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestBostonLongitudinalV2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(TestBostonLongitudinalV2.class);
    private static final String LONGITUDINAL_V2_FILE = "longitudinal-covid-v2.conf";
    private static final String STUDY_GUID = "testboston";
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
        var jdbiBlockNesting = handle.attach(JdbiBlockNesting.class);

        //
        // Load v2 definition.
        //

        Config v2Cfg = activityBuilder.readDefinitionConfig(LONGITUDINAL_V2_FILE);
        var v2Def = (FormActivityDef) gson.fromJson(ConfigUtil.toJson(v2Cfg), ActivityDef.class);
        activityBuilder.validateDefinition(v2Def);
        LOG.info("Loaded activity definition from file: {}", LONGITUDINAL_V2_FILE);

        // Extract the new question blocks.
        ConditionalBlockDef question1 = (ConditionalBlockDef) v2Def.getSections().get(0).getBlocks().get(1);
        Map<String, QuestionBlockDef> mapping = question1.getNested().stream().collect(Collectors.toMap(
                b -> ((QuestionBlockDef) b).getQuestion().getStableId(), b -> (QuestionBlockDef) b));
        QuestionBlockDef hospitalizedBlockDef = mapping.get(varsCfg.getString("id.q.longitudinal_hospitalized"));
        QuestionBlockDef hospitalNameBlockDef = mapping.get(varsCfg.getString("id.q.longitudinal_hospital_name"));
        QuestionBlockDef hospitalDaysBlockDef = mapping.get(varsCfg.getString("id.q.longitudinal_hospital_days"));

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
        LOG.info("Version {} is created with versionId={}, revisionId={}", v2VersionTag, v2Dto.getId(), v2Dto.getRevId());

        ActivityVersionDto v1Dto = handle.attach(JdbiActivityVersion.class)
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, V1_VERSION_TAG)
                .orElseThrow(() -> new DDPException("Could not find version " + V1_VERSION_TAG));
        long v1TerminatedRevId = v1Dto.getRevId(); // v1 should be terminated already after adding v2 above.
        LOG.info("Version {} is terminated with revisionId={}", V1_VERSION_TAG, v1TerminatedRevId);

        //
        // Add new question blocks to v2.
        //

        FormActivityDef currentDef = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, v2Dto);
        ConditionalBlockDef question1Def = (ConditionalBlockDef) currentDef.getSections().get(0).getBlocks().get(1);
        long parentBlockId = question1Def.getBlockId();
        long revisionId = v2Dto.getRevId();

        // New question blocks should show up between 3rd and 4th nested blocks, so use display order between 30 and 40.
        // We leave a gap in-between these new display orders so we have room to insert other stuff in the future.
        int displayOrder = 32;

        LOG.info("Adding question {} as nested block to question {} with nestedDisplayOrder={}...",
                hospitalizedBlockDef.getQuestion().getStableId(), question1Def.getControl().getStableId(), displayOrder);
        sectionBlockDao.insertBlockByType(activityId, hospitalizedBlockDef, revisionId);
        jdbiBlockNesting.insert(parentBlockId, hospitalizedBlockDef.getBlockId(), displayOrder, revisionId);

        displayOrder = 34;
        LOG.info("Adding question {} as nested block to question {} with nestedDisplayOrder={}...",
                hospitalNameBlockDef.getQuestion().getStableId(), question1Def.getControl().getStableId(), displayOrder);
        sectionBlockDao.insertBlockByType(activityId, hospitalNameBlockDef, revisionId);
        jdbiBlockNesting.insert(parentBlockId, hospitalNameBlockDef.getBlockId(), displayOrder, revisionId);

        displayOrder = 36;
        LOG.info("Adding question {} as nested block to question {} with nestedDisplayOrder={}...",
                hospitalDaysBlockDef.getQuestion().getStableId(), question1Def.getControl().getStableId(), displayOrder);
        sectionBlockDao.insertBlockByType(activityId, hospitalDaysBlockDef, revisionId);
        jdbiBlockNesting.insert(parentBlockId, hospitalDaysBlockDef.getBlockId(), displayOrder, revisionId);

        LOG.info("Done");
    }
}
