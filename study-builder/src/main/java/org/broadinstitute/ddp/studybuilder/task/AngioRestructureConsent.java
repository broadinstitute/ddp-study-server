package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiAnswer;
import org.broadinstitute.ddp.db.dao.JdbiBlock;
import org.broadinstitute.ddp.db.dao.JdbiBlockGroupHeader;
import org.broadinstitute.ddp.db.dao.JdbiBlockNesting;
import org.broadinstitute.ddp.db.dao.JdbiBlockQuestion;
import org.broadinstitute.ddp.db.dao.JdbiBlockType;
import org.broadinstitute.ddp.db.dao.JdbiCompositeAnswer;
import org.broadinstitute.ddp.db.dao.JdbiCompositeQuestion;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.JdbiListStyleHint;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiTextQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.TextQuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PresentationHint;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AngioRestructureConsent implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(AngioRestructureConsent.class);
    private static final String DATA_FILE = "patches/restructure-consent.conf";

    private Config dataCfg;
    private String studyGuid;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);

        studyGuid = dataCfg.getString("studyGuid");
        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }

        gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        restructureConsent(handle, studyDto, "consent");
        restructureConsent(handle, studyDto, "followupconsent");
        LOG.info("restructuring done");
    }

    private void restructureConsent(Handle handle, StudyDto studyDto, String key) {
        SqlHelper helper = handle.attach(SqlHelper.class);
        QuestionDao questionDao = handle.attach(QuestionDao.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        JdbiTextQuestion jdbiTextQuestion = handle.attach(JdbiTextQuestion.class);
        JdbiCompositeQuestion jdbiCompositeQuestion = handle.attach(JdbiCompositeQuestion.class);
        JdbiFormSectionBlock jdbiFormSectionBlock = handle.attach(JdbiFormSectionBlock.class);
        JdbiBlock jdbiBlock = handle.attach(JdbiBlock.class);
        JdbiBlockType jdbiBlockType = handle.attach(JdbiBlockType.class);
        JdbiBlockQuestion jdbiBlockQuestion = handle.attach(JdbiBlockQuestion.class);
        JdbiBlockGroupHeader jdbiBlockGroupHeader = handle.attach(JdbiBlockGroupHeader.class);
        JdbiBlockNesting jdbiBlockNesting = handle.attach(JdbiBlockNesting.class);
        JdbiAnswer jdbiAnswer = handle.attach(JdbiAnswer.class);
        JdbiCompositeAnswer jdbiCompositeAnswer = handle.attach(JdbiCompositeAnswer.class);

        String activityCode = dataCfg.getConfig(key).getString("activityCode");
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        LOG.info("{}: restructuring additional-agree-terms into one block...", key);

        BlockContentDto agreeTermsTitle = helper.findContentBlockByBodyText(activityId, "$angio_" + key + "_s3_additional_agree");
        BlockContentDto agreeTermsBodyV1 = helper.findContentBlockByBodyText(activityId, "$angio_" + key + "_s3_additional_agree_list");
        BlockContentDto agreeTermsBodyV2 = helper.findContentBlockByBodyText(activityId, "$angio_" + key + "_v2_s3_additional_agree_list");

        helper.assignTitleToContentBlocks(agreeTermsTitle.getBodyTemplateId(),
                Arrays.asList(agreeTermsBodyV1.getId(), agreeTermsBodyV2.getId()));
        helper.deleteContentBlock(agreeTermsTitle.getBlockId());

        LOG.info("{}: preparing consent questions...", key);

        String signatureStableId = dataCfg.getConfig(key).getString("signatureStableId");
        TextQuestionDto signature = jdbiQuestion
                .getLatestQuestionDtoByQuestionStableIdAndUmbrellaStudyId(signatureStableId, studyDto.getId())
                .map(QuestionDto::getId)
                .flatMap(jdbiTextQuestion::findDtoByQuestionId)
                .orElseThrow(() -> new DDPException("Could not find question " + signatureStableId));
        helper.updateTextQuestionInputType(signature.getId(), TextInputType.SIGNATURE);
        helper.updateTemplateSubstitutionValue(signature.getPromptTemplateId(), dataCfg.getString("signaturePrompt"));
        helper.unassignTextQuestionPlaceholderTemplate(signature.getId());
        helper.deleteTemplate(signature.getPlaceholderTemplateId());
        helper.detachQuestionFromBothSectionAndBlock(signature.getId());

        String dobStableId = dataCfg.getConfig(key).getString("dobStableId");
        QuestionDto dob = jdbiQuestion
                .getLatestQuestionDtoByQuestionStableIdAndUmbrellaStudyId(dobStableId, studyDto.getId())
                .orElseThrow(() -> new DDPException("Could not find question " + dobStableId));
        helper.updateTemplateSubstitutionValue(dob.getPromptTemplateId(), dataCfg.getString("dobPrompt"));
        helper.detachQuestionFromBothSectionAndBlock(dob.getId());

        LOG.info("{}: creating composite consent block...", key);

        String compositeJson = ConfigUtil.toJson(dataCfg.getConfig(key).getConfig("compositeDef"));
        CompositeQuestionDef composite = gson.fromJson(compositeJson, CompositeQuestionDef.class);
        questionDao.insertQuestion(signature.getActivityId(), composite, signature.getRevisionId());
        jdbiCompositeQuestion.insertChildren(composite.getQuestionId(), Arrays.asList(signature.getId(), dob.getId()));

        long compositeBlockId = jdbiBlock.insert(jdbiBlockType.getTypeId(BlockType.QUESTION), jdbiBlock.generateUniqueGuid());
        jdbiBlockQuestion.insert(compositeBlockId, composite.getQuestionId());

        LOG.info("{}: restructuring terms and composite into group block...", key);

        BlockContentDto termsTitle = helper.findContentBlockByBodyText(activityId, "$angio_" + key + "_s3_full_name_indicates");
        BlockContentDto termsBody = helper.findContentBlockByBodyText(activityId, "$angio_" + key + "_s3_full_name_indicates_list");
        helper.deleteContentBlock(termsTitle.getBlockId());

        SectionBlockMembershipDto membership = jdbiFormSectionBlock.getActiveMembershipByBlockId(termsBody.getBlockId())
                .orElseThrow(() -> new DDPException("Could not find section membership for blockId=" + termsBody.getBlockId()));
        helper.unassignBlockFromSection(termsBody.getBlockId());

        long groupBlockId = jdbiBlock.insert(jdbiBlockType.getTypeId(BlockType.GROUP), jdbiBlock.generateUniqueGuid());
        jdbiBlockGroupHeader.insert(groupBlockId, handle.attach(JdbiListStyleHint.class).getHintId(ListStyleHint.NONE),
                termsTitle.getBodyTemplateId(), membership.getRevisionId(), PresentationHint.MERGE);
        jdbiBlockNesting.insert(groupBlockId, termsBody.getBlockId(), 10, membership.getRevisionId());
        jdbiBlockNesting.insert(groupBlockId, compositeBlockId, 20, membership.getRevisionId());
        jdbiFormSectionBlock.insert(membership.getSectionId(), groupBlockId, membership.getDisplayOrder(), membership.getRevisionId());

        LOG.info("{}: backfilling answers for composite consent...", key);

        AtomicInteger instanceCount = new AtomicInteger(0);
        AtomicInteger createdCount = new AtomicInteger(0);

        helper.findAllInstancesAndConsentAnswers(studyDto.getId(), activityCode, signature.getId(), dob.getId()).forEach(info -> {
            instanceCount.incrementAndGet();
            if (info.hasEitherAnswer()) {
                String answerGuid = DBUtils.uniqueStandardGuid(handle, "answer", "answer_guid");
                long parentAnswerId = jdbiAnswer.insert(composite.getQuestionId(), info.userGuid, info.instanceId,
                        info.getEarliestAnswerCreatedAt(), info.getEarliestAnswerUpdatedAt(), answerGuid);

                List<List<Long>> childAnswerIds = Collections.singletonList(Arrays.asList(info.signatureAnswerId, info.dobAnswerId));
                jdbiCompositeAnswer.insertChildAnswerItems(parentAnswerId, childAnswerIds);

                createdCount.incrementAndGet();
            }
        });

        LOG.info("{}: backfilled {} composite answers for {} activity instances", key, createdCount.get(), instanceCount.get());
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select ai.activity_instance_id,"
                + "       u.guid as user_guid,"
                + "       sig_ans.answer_id as sig_answer_id,"
                + "       sig_ans.created_at as sig_created_at,"
                + "       sig_ans.last_updated_at as sig_updated_at,"
                + "       dob_ans.answer_id as dob_answer_id,"
                + "       dob_ans.created_at as dob_created_at,"
                + "       dob_ans.last_updated_at as dob_updated_at"
                + "  from activity_instance as ai"
                + "  join user as u on u.user_id = ai.participant_id"
                + "  join study_activity as act on act.study_activity_id = ai.study_activity_id"
                + "  left join answer as sig_ans on sig_ans.activity_instance_id = ai.activity_instance_id"
                + "       and sig_ans.question_id = :signatureQuestionId"
                + "  left join answer as dob_ans on dob_ans.activity_instance_id = ai.activity_instance_id"
                + "       and dob_ans.question_id = :dobQuestionId"
                + " where act.study_id = :studyId"
                + "   and act.study_activity_code = :activityCode")
        @RegisterConstructorMapper(InstanceInfo.class)
        Stream<InstanceInfo> findAllInstancesAndConsentAnswers(@Bind("studyId") long studyId,
                                                               @Bind("activityCode") String activityCode,
                                                               @Bind("signatureQuestionId") long signatureQuestionId,
                                                               @Bind("dobQuestionId") long dobQuestionId);

        @SqlQuery("select bt.* from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
                + " where tmpl.template_text = :text"
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

        default void assignTitleToContentBlocks(long titleTemplateId, List<Long> blockContentIds) {
            int numUpdated = _updateBlockContentTitleTemplateId(titleTemplateId, blockContentIds);
            if (numUpdated != blockContentIds.size()) {
                throw new DDPException(String.format(
                        "Expected to assign title template to %d content blocks but updated %d",
                        blockContentIds.size(), numUpdated));
            }
        }

        default void unassignBlockFromSection(long blockId) {
            int numDeleted = _deleteSectionBlockMembershipByBlockId(blockId);
            if (numDeleted != 1) {
                throw new DDPException("Could not remove block with blockId=" + blockId + " from section");
            }
        }

        default void deleteContentBlock(long blockId) {
            int numDeleted = _deleteSectionBlockMembershipByBlockId(blockId);
            if (numDeleted != 1) {
                throw new DDPException("Could not remove content block with blockId=" + blockId + " from section");
            }

            numDeleted = _deleteBlockContentByBlockId(blockId);
            if (numDeleted != 1) {
                throw new DDPException("Could not delete content block with blockId=" + blockId);
            }

            numDeleted = _deleteBlockById(blockId);
            if (numDeleted != 1) {
                throw new DDPException("Could not delete block with blockId=" + blockId);
            }
        }

        default void unassignTextQuestionPlaceholderTemplate(long questionId) {
            int numUpdated = _updatePlaceholderTemplateIdByQuestionId(questionId, null);
            if (numUpdated != 1) {
                throw new DDPException(String.format(
                        "Expected to update 1 text question with questionId=%d but updated %d", questionId, numUpdated));
            }
        }

        default void updateTextQuestionInputType(long questionId, TextInputType type) {
            int numUpdated = _updateInputTypeByQuestionId(questionId, type);
            if (numUpdated != 1) {
                throw new DDPException(String.format(
                        "Expected to update 1 input type for questionId=%d but updated %d", questionId, numUpdated));
            }
        }

        default void updateTemplateSubstitutionValue(long templateId, String newValue) {
            int numUpdated = _updateTemplateVarValueByTemplateId(templateId, newValue);
            if (numUpdated != 1) {
                throw new DDPException(String.format(
                        "Expected to update 1 substitution value for templateId=%d but updated %d", templateId, numUpdated));
            }
        }

        default void deleteTemplate(long templateId) {
            int numDeleted = _deleteTemplateSubstitutionsByTemplateId(templateId);
            if (numDeleted != 1) {
                throw new DDPException("Could not delete template substitutions for templateId=" + templateId);
            }

            numDeleted = _deleteTemplateVariablesByTemplateId(templateId);
            if (numDeleted != 1) {
                throw new DDPException("Could not delete template variables for templateId=" + templateId);
            }

            numDeleted = _deleteTemplateById(templateId);
            if (numDeleted != 1) {
                throw new DDPException("Could not delete template with templateId=" + templateId);
            }
        }

        default void detachQuestionFromBothSectionAndBlock(long questionId) {
            int numDeleted = _deleteSectionBlockMembershipByQuestionId(questionId);
            if (numDeleted != 1) {
                throw new DDPException("Could not remove question with questionId=" + questionId + " from section");
            }

            numDeleted = _deleteBlockQuestionByQuestionId(questionId);
            if (numDeleted != 1) {
                throw new DDPException("Could not remove question with questionId=" + questionId + " from block");
            }
        }

        @SqlUpdate("update block_content set title_template_id = :titleTemplateId where block_content_id in (<blockContentIds>)")
        int _updateBlockContentTitleTemplateId(@Bind("titleTemplateId") long titleTemplateId,
                                               @BindList("blockContentIds") List<Long> blockContentIds);

        @SqlUpdate("delete from form_section__block where block_id = :blockId")
        int _deleteSectionBlockMembershipByBlockId(@Bind("blockId") long blockId);

        @SqlUpdate("delete from block_content where block_id = :blockId")
        int _deleteBlockContentByBlockId(@Bind("blockId") long blockContentId);

        @SqlUpdate("delete from block where block_id = :id")
        int _deleteBlockById(@Bind("id") long id);

        @SqlUpdate("update text_question"
                + "    set input_type_id = ("
                + "        select text_question_input_type_id from text_question_input_type where text_question_input_type_code = :type)"
                + "  where question_id = :questionId")
        int _updateInputTypeByQuestionId(@Bind("questionId") long questionId, @Bind("type") TextInputType type);

        @SqlUpdate("update i18n_template_substitution as i18n"
                + "   join template_variable as var on var.template_variable_id = i18n.template_variable_id"
                + "    set i18n.substitution_value = :value"
                + "  where var.template_id = :templateId")
        int _updateTemplateVarValueByTemplateId(@Bind("templateId") long templateId, @Bind("value") String value);

        @SqlUpdate("update text_question set placeholder_template_id = :placeholderId where question_id = :questionId")
        int _updatePlaceholderTemplateIdByQuestionId(@Bind("questionId") long questionId,
                                                     @Bind("placeholderId") Long placeholderTemplateId);

        @SqlUpdate("delete i18n from i18n_template_substitution as i18n"
                + "   join template_variable as var on var.template_variable_id = i18n.template_variable_id"
                + "  where var.template_id = :templateId")
        int _deleteTemplateSubstitutionsByTemplateId(@Bind("templateId") long templateId);

        @SqlUpdate("delete from template_variable where template_id = :templateId")
        int _deleteTemplateVariablesByTemplateId(@Bind("templateId") long templateId);

        @SqlUpdate("delete from template where template_id = :id")
        int _deleteTemplateById(@Bind("id") long templateId);

        @SqlUpdate("delete from form_section__block"
                + "  where block_id in (select block_id from block__question where question_id = :questionId)")
        int _deleteSectionBlockMembershipByQuestionId(@Bind("questionId") long questionId);

        @SqlUpdate("delete from block__question where question_id = :questionId")
        int _deleteBlockQuestionByQuestionId(@Bind("questionId") long questionId);
    }

    public static class InstanceInfo {
        final long instanceId;
        final String userGuid;
        final Long signatureAnswerId;
        final Long signatureCreatedAt;
        final Long signatureUpdatedAt;
        final Long dobAnswerId;
        final Long dobCreatedAt;
        final Long dobUpdatedAt;

        @JdbiConstructor
        public InstanceInfo(@ColumnName("activity_instance_id") long instanceId,
                            @ColumnName("userGuid") String userGuid,
                            @ColumnName("sig_answer_id") Long signatureAnswerId,
                            @ColumnName("sig_created_at") Long signatureCreatedAt,
                            @ColumnName("sig_updated_at") Long signatureUpdatedAt,
                            @ColumnName("dob_answer_id") Long dobAnswerId,
                            @ColumnName("dob_created_at") Long dobCreatedAt,
                            @ColumnName("dob_updated_at") Long dobUpdatedAt) {
            this.instanceId = instanceId;
            this.userGuid = userGuid;
            this.signatureAnswerId = signatureAnswerId;
            this.signatureCreatedAt = signatureCreatedAt;
            this.signatureUpdatedAt = signatureUpdatedAt;
            this.dobAnswerId = dobAnswerId;
            this.dobCreatedAt = dobCreatedAt;
            this.dobUpdatedAt = dobUpdatedAt;
        }

        public boolean hasEitherAnswer() {
            return signatureAnswerId != null || dobAnswerId != null;
        }

        public long getEarliestAnswerCreatedAt() {
            if (signatureCreatedAt == null) {
                return dobCreatedAt;
            } else if (dobCreatedAt == null) {
                return signatureCreatedAt;
            } else {
                return Math.min(signatureCreatedAt, dobCreatedAt);
            }
        }

        public long getEarliestAnswerUpdatedAt() {
            if (signatureUpdatedAt == null) {
                return dobUpdatedAt;
            } else if (dobUpdatedAt == null) {
                return signatureUpdatedAt;
            } else {
                return Math.min(signatureUpdatedAt, dobUpdatedAt);
            }
        }
    }
}
