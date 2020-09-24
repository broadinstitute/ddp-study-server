package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiPicklistOption;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task to migrate Brain prequal to v2
 */
public class BrainPrequalV2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(BrainPrequalV2.class);
    private static final String DATA_FILE = "patches/prequal-v2.conf";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        LOG.info("Executing BrainPrequalV2 task...");

        String activityCode = dataCfg.getString("activityCode");
        String versionTag = dataCfg.getString("versionTag");
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        Instant timestamp = Instant.parse(dataCfg.getString("timestamp"));
        String studyGuid = studyDto.getGuid();
        long studyId = studyDto.getId();
        long activityId = ActivityBuilder.findActivityId(handle, studyId, activityCode);

        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .orElseThrow(() -> new DDPException("Could not find id for activity " + activityCode + " and study id " + studyGuid));

        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyGuid, activityCode, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getUserId(), reason);
        SqlHelper helper = handle.attach(SqlHelper.class);
        String selfOptionText = "I have been diagnosed with brain tumor.";
        String promptText = "First, how will you be participating in the Brain Tumor Project? ";

        //load new activity def/conf
        ActivityDef def = GsonUtil.standardGson().fromJson(ConfigUtil.toJson(dataCfg), ActivityDef.class);

        //load current activity def from db
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        ActivityDao activityDao = handle.attach(ActivityDao.class);
        ActivityVersionDto currentActivityVerDto = activityDao.getJdbiActivityVersion().findByActivityCodeAndVersionTag(
                studyId, activityCode, "v1").get();
        ActivityDef currActivityDef = activityDao.findDefByDtoAndVersion(activityDto, currentActivityVerDto);
        FormActivityDef currFormActivityDef = (FormActivityDef)currActivityDef;

        //change version
        ActivityVersionDto activityVersionDto = activityDao.changeVersion(activityId, versionTag, meta);

        //remove study activity title
        helper.updateActivityTitle(activityDto.getActivityId(), "");

        String templateVarName = "brain_prequal_contact_title";
        long templateId = helper.findTemplateIdByVariableName(templateVarName);
        long templateRevId = helper.findTemplateRevisionId(templateId);
        long newTemplateRevId = jdbiRevision.copyAndTerminate(templateRevId, meta);
        //may be look for template brain_prequal_contact_title and disable rather than these sql updates !!
        helper.updateTemplateRevision(templateId, newTemplateRevId);
        long tmpVarId = helper.findTemplateVariableId(templateId);
        helper.updateVarSubstitutionValue(tmpVarId, "");

        //populate section with ageup questions
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        List<FormSectionDef> bodySections = ((FormActivityDef) def).getSections();
        //skip first section and insert second section
        List<FormSectionDef> newSections = List.of(bodySections.get(1));
        sectionBlockDao.insertBodySections(activityId, newSections, activityVersionDto.getRevId());

        //disable Name questions
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        QuestionDao questionDao = handle.attach(QuestionDao.class);
        JdbiPicklistOption jdbiPicklistOption = handle.attach(JdbiPicklistOption.class);
        QuestionDto questionDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "PREQUAL_FIRST_NAME").get();
        questionDao.disableTextQuestion(questionDto.getId(), meta);
        //update rev in nested blocks
        long currRevId = helper.findNestedQuestionBlockRevisionId(questionDto.getId());
        long newRevId = jdbiRevision.copyAndTerminate(currRevId, meta);
        long nestedBlockId = helper.findQuestionBlockId(questionDto.getId());
        helper.updateNestedQuestionRevision(nestedBlockId, newRevId);

        questionDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "PREQUAL_LAST_NAME").get();
        questionDao.disableTextQuestion(questionDto.getId(), meta);
        nestedBlockId = helper.findQuestionBlockId(questionDto.getId());
        helper.updateNestedQuestionRevision(nestedBlockId, newRevId);
        LOG.info("Disabled first_name and last_name questions");

        //disable copy first_name and last_name events
        List<Long> eventIds = helper.findStudyCopyEventIds(studyId);
        if (eventIds.size() != 2) {
            throw new RuntimeException("Expecting two COPY events for Brain, got :" + eventIds.size() + "  aborting patch ");
        }
        helper.disableStudyEvents(Set.copyOf(eventIds));
        LOG.info("Disabled first_name and last_name COPY events");

        //modify PL question "PREQUAL_SELF_DESCRIBE"
        //update prompt text
        questionDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, "PREQUAL_SELF_DESCRIBE").get();
        long selfQuestionVarId = helper.findTemplateVariableId(questionDto.getPromptTemplateId());
        helper.updateVarSubstitutionValue(selfQuestionVarId, promptText);

        //disable PREQUAL_SELF_DESCRIBE.MAILING_LIST option
        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);
        plQuestionDao.disableOption(questionDto.getId(), "MAILING_LIST", meta);
        LOG.info("Disabled mailing_list option");

        //update PREQUAL_SELF_DESCRIBE.DIAGNOSIED option prompt
        PicklistOptionDto selfOptionDto = jdbiPicklistOption.getActiveByStableId(questionDto.getId(), "DIAGNOSED").get();
        long selfOptTemplateId = selfOptionDto.getOptionLabelTemplateId();
        long selfOptTemplateVarId = helper.findTemplateVariableId(selfOptTemplateId);
        helper.updateVarSubstitutionValue(selfOptTemplateVarId, selfOptionText);

        //add option PREQUAL_SELF_DESCRIBE.CHILD_DIAGNOSED
        FormSectionDef plQuestionSec = bodySections.get(0);
        FormBlockDef formBlockDef = plQuestionSec.getBlocks().get(0);
        QuestionDef questionDef1 = formBlockDef.getQuestions().findFirst().get();
        PicklistQuestionDef plQuestionDef = (PicklistQuestionDef) questionDef1;
        List<PicklistOptionDef> options = plQuestionDef.getAllPicklistOptions();
        for (PicklistOptionDef opt : options) {
            if (opt.getStableId().equals("CHILD_DIAGNOSED")) {
                plQuestionDao.addOption(questionDto.getId(), opt, 2, RevisionDto.fromStartMetadata(activityVersionDto.getRevId(), meta));
            }
        }

        long introBlockId = currFormActivityDef.getIntroduction().getBlocks().get(0).getBlockId();
        sectionBlockDao.disableBlock(introBlockId, meta);
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update i18n_template_substitution set substitution_value = :value where template_variable_id = :id")
        int _updateVarValueByTemplateVarId(@Bind("id") long templateVarId, @Bind("value") String value);

        default void updateVarSubstitutionValue(long templateVarId, String value) {
            int numUpdated = _updateVarValueByTemplateVarId(templateVarId, value);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 template variable value for templateVarId="
                        + templateVarId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update i18n_study_activity set title = :value where study_activity_id = :studyActivityId")
        int _updateActivityTitle(@Bind("studyActivityId") long studyActivityId, @Bind("value") String value);

        default void updateActivityTitle(long studyActivityId, String value) {
            int numUpdated = _updateActivityTitle(studyActivityId, value);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 row for studyActivityId="
                        + studyActivityId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update block_nesting set revision_id = :revisionId where nested_block_id = :nestedBlockId")
        int _updateNestedQuestionRevision(@Bind("nestedBlockId") long nestedBlockId, @Bind("revisionId") long revisionId);

        default void updateNestedQuestionRevision(long nestedBlockId, long revisionId) {
            int numUpdated = _updateNestedQuestionRevision(nestedBlockId, revisionId);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 row of nested block ="
                        + nestedBlockId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update template set revision_id = :revisionId where template_id = :templateId")
        int _updateTemplateRevision(@Bind("templateId") long templateId, @Bind("revisionId") long revisionId);

        default void updateTemplateRevision(long templateId, long revisionId) {
            int numUpdated = _updateTemplateRevision(templateId, revisionId);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 row of templateId ="
                        + templateId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update event_configuration set is_active = false where event_configuration_id in (<eventIds>)")
        int disableStudyEvents(@BindList("eventIds")Set<Long> eventIds);


        @SqlQuery("select event_configuration_id from event_configuration c, event_action ea,  event_action_type eat, "
                + "event_trigger et, event_trigger_type tt "
                + "where c.event_trigger_id = et.event_trigger_id and tt.event_trigger_type_id = et.event_trigger_type_id "
                + "and ea.event_action_id = c.event_action_id and eat.event_action_type_id = ea.event_action_type_id "
                + "and tt.event_trigger_type_code = 'ACTIVITY_STATUS' "
                + "and eat.event_action_type_code = 'COPY_ANSWER' "
                + "and c.umbrella_study_id = :studyId")
        List<Long> findStudyCopyEventIds(@Bind("studyId") long studyId);

        @SqlQuery("select nb.revision_id from block_nesting nb, block__question bq "
                + " where bq.block_id = nb.nested_block_id and bq.question_id = :questionId")
        long findNestedQuestionBlockRevisionId(@Bind("questionId") long questionId);

        @SqlQuery("select block_id from block__question where question_id = :questionId")
        int _findQuestionBlockId(@Bind("questionId") long questionId);

        default long findQuestionBlockId(long questionId) {
            int blockId = _findQuestionBlockId(questionId);
            return blockId;
        }

        //WATCH OUT: error prone if same variable name across multiple studies.. ok for patch though.
        //used max to handle multiple study versions in lower regions
        @SqlQuery("select max(template_id) from template_variable where variable_name = :varName")
        long findTemplateIdByVariableName(@Bind("varName") String varName);

        @SqlQuery("select revision_id from template where template_id = :templateId")
        long findTemplateRevisionId(@Bind("templateId") long templateId);

        @SqlQuery("select template_variable_id from template_variable where template_id = :templateId")
        long findTemplateVariableId(@Bind("templateId") long templateId);
    }

}
