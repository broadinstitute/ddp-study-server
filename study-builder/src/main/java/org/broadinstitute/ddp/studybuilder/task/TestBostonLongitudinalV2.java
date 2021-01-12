package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiBlockNesting;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestBostonLongitudinalV2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(TestBostonLongitudinalV2.class);
    private static final String LONGITUDINAL_V2_FILE = "longitudinal-covid-v2.conf";
    private static final String VACCINE_COPY_EVENT_FILE = "patches/vaccine-copy-answer-event.conf";
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
        var eventBuilder = new EventBuilder(studyCfg, studyDto, adminUser.getId());

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

        // Extract the new question block definitions.
        FormSectionDef v2SectionDef = v2Def.getSections().get(0);
        ConditionalBlockDef v2Question1 = (ConditionalBlockDef) v2SectionDef.getBlocks().get(1);

        Map<String, QuestionBlockDef> mapping1 = v2Question1.getNested().stream()
                .map(b -> (QuestionBlockDef) b)
                .collect(Collectors.toMap(b -> b.getQuestion().getStableId(), Function.identity()));
        QuestionBlockDef hospitalizedBlockDef = mapping1.get(varsCfg.getString("id.q.longitudinal_hospitalized"));
        QuestionBlockDef hospitalNameBlockDef = mapping1.get(varsCfg.getString("id.q.longitudinal_hospital_name"));
        QuestionBlockDef hospitalDaysBlockDef = mapping1.get(varsCfg.getString("id.q.longitudinal_hospital_days"));

        Map<String, ConditionalBlockDef> mapping2 = v2SectionDef.getBlocks().stream()
                .filter(b -> b.getBlockType() == BlockType.CONDITIONAL)
                .map(b -> (ConditionalBlockDef) b)
                .collect(Collectors.toMap(b -> b.getControl().getStableId(), Function.identity()));
        ConditionalBlockDef vaccineStudyBlockDef = mapping2.get(varsCfg.getString("id.q.longitudinal_vaccine_study"));
        ConditionalBlockDef vaccineReceivedBlockDef = mapping2.get(varsCfg.getString("id.q.longitudinal_vaccine_received"));

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
        FormSectionDef currentSectionDef = currentDef.getSections().get(0);
        ConditionalBlockDef question1Def = (ConditionalBlockDef) currentSectionDef.getBlocks().get(1);
        long sectionId = currentSectionDef.getSectionId();
        long parentBlockId = question1Def.getBlockId();
        long revisionId = v2Dto.getRevId();

        // Hospital question blocks should show up between 3rd and 4th nested blocks, so use display order between 30 and 40.
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

        // Vaccine question blocks should show up after Flu Shot question.
        String fluShotStableId = varsCfg.getString("id.q.flu_shot");
        QuestionBlockDef fluShotBlockDef = currentSectionDef.getBlocks().stream()
                .filter(b -> b.getBlockType() == BlockType.QUESTION)
                .map(b -> (QuestionBlockDef) b)
                .filter(b -> b.getQuestion().getStableId().equals(fluShotStableId))
                .findFirst().get();
        displayOrder = handle.attach(JdbiFormSectionBlock.class)
                .getActiveMembershipByBlockId(fluShotBlockDef.getBlockId())
                .map(SectionBlockMembershipDto::getDisplayOrder)
                .get() + 3;

        LOG.info("Adding question block {} to section {} with displayOrder={}...",
                vaccineStudyBlockDef.getControl().getStableId(), sectionId, displayOrder);
        sectionBlockDao.insertBlockForSection(activityId, sectionId, displayOrder, vaccineStudyBlockDef, revisionId);

        displayOrder += 3;
        LOG.info("Adding question block {} to section {} with displayOrder={}...",
                vaccineReceivedBlockDef.getControl().getStableId(), sectionId, displayOrder);
        sectionBlockDao.insertBlockForSection(activityId, sectionId, displayOrder, vaccineReceivedBlockDef, revisionId);

        //
        // Ensure we have longitudinal copy answer event configuration.
        //

        List<EventConfiguration> events = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId());

        LOG.info("Searching for longitudinal copy answer event configuration...");
        EventConfiguration copyEvent = null;
        for (var event : events) {
            if (event.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS) {
                var trigger = (ActivityStatusChangeTrigger) event.getEventTrigger();
                if (trigger.getStudyActivityId() == activityId && trigger.getInstanceStatusType() == InstanceStatusType.CREATED) {
                    if (event.getEventActionType() == EventActionType.COPY_ANSWER) {
                        copyEvent = event;
                        break;
                    }
                }
            }
        }

        if (copyEvent != null) {
            LOG.info("Already has longitudinal copy answer event config with id={}", copyEvent.getEventConfigurationId());
        } else {
            LOG.info("Did not find longitudinal copy answer event, creating...");

            // Load the copy answer event definition.
            File file = cfgPath.getParent().resolve(VACCINE_COPY_EVENT_FILE).toFile();
            if (!file.exists()) {
                throw new DDPException("Data file is missing: " + file);
            }
            Config copyEventCfg = ConfigFactory.parseFile(file);
            copyEventCfg = copyEventCfg.resolveWith(varsCfg);

            eventBuilder.insertEvent(handle, copyEventCfg);
        }

        LOG.info("Done");
    }
}
