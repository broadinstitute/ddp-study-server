package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
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
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-off task to add "confirmed contact" question to adhoc survey in deployed environments.
 */
public class TestBostonAddConfirmedContactQuestion implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(TestBostonAddConfirmedContactQuestion.class);
    private static final String STUDY_GUID = "testboston";
    private static final String ADHOC_SYMPTOM_FILE = "adhoc-symptom.conf";

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

        Config defCfg = activityBuilder.readDefinitionConfig(ADHOC_SYMPTOM_FILE);
        LOG.info("Loaded activity definition from file: {}", ADHOC_SYMPTOM_FILE);

        String activityCode = defCfg.getString("activityCode");
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY_GUID, activityCode)
                .orElseThrow(() -> new DDPException("Could not find activity " + activityCode + " in study " + STUDY_GUID));

        String versionTag = defCfg.getString("versionTag");
        ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, versionTag)
                .orElseThrow(() -> new DDPException("Could not find version " + versionTag));

        FormActivityDef currentDef = handle.attach(FormActivityDao.class).findDefByDtoAndVersion(activityDto, versionDto);
        LOG.info("Loaded {} activity definition from database", activityCode);

        String questionStableId = varsCfg.getString("id.q.confirmed_contact");
        LOG.info("Searching for question {}...", questionStableId);

        FormSectionDef currentSectionDef = currentDef.getSections().get(0);
        QuestionDef currentQuestionDef = currentSectionDef.getBlocks().stream()
                .flatMap(FormBlockDef::getQuestions)
                .filter(questionDef -> questionDef.getStableId().equals(questionStableId))
                .findFirst().orElse(null);

        if (currentQuestionDef != null) {
            LOG.info("Activity already contains question {} with questionId={}", questionStableId, currentQuestionDef.getQuestionId());
        } else {
            LOG.info("Activity does not contain question {}, adding it directly...", questionStableId);

            long activityId = activityDto.getActivityId();
            long sectionId = currentSectionDef.getSectionId();
            long revisionId = versionDto.getRevId();    // add it to existing version

            var newDef = (FormActivityDef) gson.fromJson(ConfigUtil.toJson(defCfg), ActivityDef.class);
            FormBlockDef newBlockDef = newDef.getSections().get(0)
                    .getBlocks().stream()
                    .filter(blockDef -> blockDef.getQuestions()
                            .anyMatch(questionDef -> questionDef.getStableId().equals(questionStableId)))
                    .findFirst().get();

            int newDisplayOrder = handle.attach(JdbiFormSectionBlock.class)
                    .getOrderedActiveMemberships(sectionId)
                    .stream()
                    .mapToInt(SectionBlockMembershipDto::getDisplayOrder)
                    .max()
                    .getAsInt() + SectionBlockDao.DISPLAY_ORDER_GAP;

            handle.attach(SectionBlockDao.class)
                    .insertBlockForSection(activityId, sectionId, newDisplayOrder, newBlockDef, revisionId);
            LOG.info("Finished inserting new question for {}", questionStableId);
        }
    }
}
