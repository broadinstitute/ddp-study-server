package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Objects;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import one.util.streamex.StreamEx;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.AgreementQuestionDto;
import org.broadinstitute.ddp.db.dto.BooleanQuestionDto;
import org.broadinstitute.ddp.db.dto.BlockTabularQuestionDto;
import org.broadinstitute.ddp.db.dto.CompositeQuestionDto;
import org.broadinstitute.ddp.db.dto.DateQuestionDto;
import org.broadinstitute.ddp.db.dto.FileQuestionDto;
import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.broadinstitute.ddp.db.dto.NumericQuestionDto;
import org.broadinstitute.ddp.db.dto.DecimalQuestionDto;
import org.broadinstitute.ddp.db.dto.EquationQuestionDto;
import org.broadinstitute.ddp.db.dto.PicklistGroupDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.PicklistQuestionDto;
import org.broadinstitute.ddp.db.dto.MatrixGroupDto;
import org.broadinstitute.ddp.db.dto.MatrixOptionDto;
import org.broadinstitute.ddp.db.dto.MatrixQuestionDto;
import org.broadinstitute.ddp.db.dto.MatrixRowDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.TextQuestionDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceSelectQuestionDto;
import org.broadinstitute.ddp.db.dto.TypedQuestionId;
import org.broadinstitute.ddp.db.dto.validation.RuleDto;
import org.broadinstitute.ddp.equation.QuestionEvaluator;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DecimalQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.EquationQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.ActivityInstanceSelectQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.ActivityInstanceSelectAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.EquationAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DecimalAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.MatrixAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.AgreementQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DatePicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.FileQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DecimalQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.EquationQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixGroup;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixOption;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.MatrixRow;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.ActivityInstanceSelectQuestion;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.util.FileUploadValidator;
import org.broadinstitute.ddp.util.QuestionUtil;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface QuestionDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(QuestionDao.class);
    int DISPLAY_ORDER_GAP = 10;

    @CreateSqlObject
    JdbiActivity getJdbiActivity();

    @CreateSqlObject
    JdbiQuestionStableCode getJdbiQuestionStableCode();

    @CreateSqlObject
    JdbiQuestion getJdbiQuestion();

    @CreateSqlObject
    JdbiQuestionType getJdbiQuestionType();

    @CreateSqlObject
    JdbiBooleanQuestion getJdbiBooleanQuestion();

    @CreateSqlObject
    JdbiTextQuestion getJdbiTextQuestion();

    @CreateSqlObject
    JdbiTextQuestionSuggestion getJdbiTextQuestionSuggestion();

    @CreateSqlObject
    JdbiActivityInstanceSelectQuestion getJdbiActivityInstanceSelectQuestion();

    @CreateSqlObject
    JdbiActivityInstanceSelectActivityCodes getJdbiActivityInstanceSelectActivityCodes();

    @CreateSqlObject
    JdbiDateQuestion getJdbiDateQuestion();

    @CreateSqlObject
    JdbiDateQuestionFieldOrder getJdbiDateQuestionFieldOrder();

    @CreateSqlObject
    JdbiDateFieldType getJdbiDateFieldType();

    @CreateSqlObject
    JdbiDateRenderMode getJdbiDateRenderMode();

    @CreateSqlObject
    JdbiDateQuestionMonthPicklist getJdbiDateQuestionMonthPicklist();

    @CreateSqlObject
    JdbiDateQuestionYearPicklist getJdbiDateQuestionYearPicklist();

    @CreateSqlObject
    JdbiPicklistQuestion getJdbiPicklistQuestion();

    @CreateSqlObject
    JdbiMatrixQuestion getJdbiMatrixQuestion();

    @CreateSqlObject
    JdbiMatrixGroup getJdbiMatrixGroup();

    @CreateSqlObject
    JdbiPicklistOption getJdbiPicklistOption();

    @CreateSqlObject
    JdbiCompositeQuestion getJdbiCompositeQuestion();

    @CreateSqlObject
    JdbiNumericQuestion getJdbiNumericQuestion();

    @CreateSqlObject
    JdbiDecimalQuestion getJdbiDecimalQuestion();

    @CreateSqlObject
    JdbiEquationQuestion getJdbiEquationQuestion();

    @CreateSqlObject
    JdbiBlockQuestion getJdbiBlockQuestion();

    @CreateSqlObject
    JdbiQuestionValidation getJdbiQuestionValidation();

    @CreateSqlObject
    JdbiRevision getJdbiRevision();

    @CreateSqlObject
    ValidationDao getValidationDao();

    @CreateSqlObject
    TemplateDao getTemplateDao();

    @CreateSqlObject
    PicklistQuestionDao getPicklistQuestionDao();

    @CreateSqlObject
    MatrixQuestionDao getMatrixQuestionDao();

    @CreateSqlObject
    JdbiAgreementQuestion getJdbiAgreementQuestion();

    @CreateSqlObject
    AnswerDao getAnswerDao();

    /**
     * Get the non-deprecated question for a given block.
     *
     * @param blockId                 the block id
     * @param activityInstanceGuid    the form instance guid
     * @param instanceCreatedAtMillis the timestamp of when instance was created
     * @return single question, if it's not deprecated
     */
    default Optional<Question> getQuestionByBlockId(long blockId,
                                                    String activityInstanceGuid,
                                                    long instanceCreatedAtMillis,
                                                    long langCodeId) {
        return getQuestionByBlockId(blockId, activityInstanceGuid, instanceCreatedAtMillis, false, langCodeId);
    }


    /**
     * Get the question for a given block. Toggle if okay with deprecated questions.
     *
     * @param blockId                 the block id
     * @param activityInstanceGuid    the form instance guid
     * @param instanceCreatedAtMillis the timestamp of when instance was created
     * @param includeDeprecated       flag indicating whether or nto to include deprecated questions
     * @return single question, if it's not deprecated
     */
    default Optional<Question> getQuestionByBlockId(long blockId,
                                                    String activityInstanceGuid,
                                                    long instanceCreatedAtMillis,
                                                    boolean includeDeprecated,
                                                    long langCodeId) {
        QuestionDto dto = getJdbiBlockQuestion()
                .findQuestionId(blockId, activityInstanceGuid)
                .flatMap(questionId -> getJdbiQuestion().findQuestionDtoById(questionId))
                .orElseThrow(() -> new DaoException(String.format(
                        "No question found for block %d and activity instance %s", blockId, activityInstanceGuid)));
        if (dto.isDeprecated() && !includeDeprecated) {
            LOG.info("Question id {} for block id {}, instance guid {} is deprecated",
                    dto.getId(), blockId, activityInstanceGuid);
            return Optional.empty();
        } else {
            // Use of() instead of ofNullable() since it should be non-null.
            return Optional.of(getQuestionByActivityInstanceAndDto(dto, activityInstanceGuid, instanceCreatedAtMillis, langCodeId));
        }
    }

    /**
     * Get the non-deprecated control question for a conditional block.
     *
     * @param blockId                 the block id
     * @param activityInstanceGuid    the form instance guid
     * @param instanceCreatedAtMillis the timestamp of when instance was created
     * @return control question, if it's not deprecated
     */
    default Optional<Question> getControlQuestionByBlockId(long blockId,
                                                           String activityInstanceGuid,
                                                           long instanceCreatedAtMillis,
                                                           long langCodeId) {
        return getControlQuestionByBlockId(blockId, activityInstanceGuid, instanceCreatedAtMillis, false, langCodeId);
    }

    /**
     * Get the control question for a conditional block. This allows fetching deprecated control questions. Prefer the
     * other method that excludes them.
     *
     * @param blockId                 the block id
     * @param activityInstanceGuid    the form instance guid
     * @param instanceCreatedAtMillis the timestamp of when instance was created
     * @return control question
     */
    default Optional<Question> getControlQuestionByBlockId(long blockId,
                                                           String activityInstanceGuid,
                                                           long instanceCreatedAtMillis,
                                                           boolean includeDeprecated,
                                                           long langCodeId) {
        QuestionDto questionDto = getHandle().attach(JdbiBlockConditionalControl.class)
                .findControlQuestionId(blockId, activityInstanceGuid)
                .flatMap(questionId -> getJdbiQuestion().findQuestionDtoById(questionId))
                .orElseThrow(() -> new DaoException("No control question found for block " + blockId
                        + " and activity instance " + activityInstanceGuid));
        if (questionDto.isDeprecated() && !includeDeprecated) {
            LOG.info("Skipping deprecated control question with id {} for block id {}, instance guid {}",
                    questionDto.getId(), blockId, activityInstanceGuid);
            return Optional.empty();
        } else {
            return Optional.of(getQuestionByActivityInstanceAndDto(questionDto, activityInstanceGuid,
                    instanceCreatedAtMillis, true, langCodeId));
        }
    }

    /**
     * Get a question for a given id. Gets answers as well if it has any.
     *
     * @param questionId              the block id
     * @param activityInstanceGuid    the form instance guid
     * @param instanceCreatedAtMillis the timestamp of when instance was created
     * @return single question
     */
    default Question getQuestionByIdAndActivityInstanceGuid(long questionId, String activityInstanceGuid,
                                                            long instanceCreatedAtMillis, long langCodeId) {
        return getQuestionByIdAndActivityInstanceGuid(questionId, activityInstanceGuid, instanceCreatedAtMillis, true, langCodeId);
    }

    /**
     * Get a question for a given id. Toggle get answers as well if it has any.
     *
     * @param questionId              the block id
     * @param activityInstanceGuid    the form instance guid
     * @param instanceCreatedAtMillis the timestamp of when instance was created
     * @return single question
     */
    default Question getQuestionByIdAndActivityInstanceGuid(
            long questionId,
            String activityInstanceGuid,
            long instanceCreatedAtMillis,
            boolean retrieveAnswers,
            long langCodeId
    ) {
        return getJdbiQuestion().findQuestionDtoById(questionId)
                .filter(dto -> dto.getRevisionEnd() == null)
                .map(dto -> getQuestionByActivityInstanceAndDto(dto, activityInstanceGuid,
                        instanceCreatedAtMillis, retrieveAnswers, langCodeId))
                .orElseThrow(() -> new DaoException(String.format("No question found with id %d", questionId)));
    }

    /**
     * Get a question for a given dto and user. Toggle retrieve answers.
     *
     * @param dto             the question dto
     * @param userGuid        the user guid
     * @param retrieveAnswers flag indicating whether to get answers for question
     * @return single question
     */
    default Question getQuestionByUserGuidAndQuestionDto(QuestionDto dto,
                                                         String userGuid,
                                                         boolean retrieveAnswers,
                                                         long langCodeId) {
        return getHandle().attach(JdbiActivityInstance.class)
                .findLatestInstanceFromUserGuidAndQuestionId(userGuid, dto.getId())
                .map(instanceDto -> getQuestionByActivityInstanceAndDto(dto, instanceDto.getGuid(),
                        instanceDto.getCreatedAtMillis(), retrieveAnswers, langCodeId))
                .orElseThrow(null);
    }

    /**
     * Get question by activity instance and dto. Gets answers as well if it has any.
     *
     * @param dto                     question dto
     * @param activityInstanceGuid    the form instance guid
     * @param instanceCreatedAtMillis the timestamp of when instance was created
     * @return single question
     */
    default Question getQuestionByActivityInstanceAndDto(QuestionDto dto, String activityInstanceGuid,
                                                         long instanceCreatedAtMillis, long langCodeId) {
        return getQuestionByActivityInstanceAndDto(dto, activityInstanceGuid, instanceCreatedAtMillis, true, langCodeId);
    }

    /**
     * Get question by activity instance and dto. Toggles getting answers as well if it has any.
     *
     * @param dto                     question dto
     * @param activityInstanceGuid    the form instance guid
     * @param instanceCreatedAtMillis the timestamp of when instance was created
     * @param retrieveAnswers         flag indicating whether to get answers for question
     * @return single question
     */
    default Question getQuestionByActivityInstanceAndDto(QuestionDto dto,
                                                         String activityInstanceGuid,
                                                         long instanceCreatedAtMillis,
                                                         boolean retrieveAnswers,
                                                         long langCodeId) {
        if (dto == null) {
            throw new DaoException("No question dto found");
        }

        List<Long> answerIds = new ArrayList<>();
        if (retrieveAnswers) {
            answerIds = List.copyOf(getAnswerDao().getAnswerSql()
                    .findAnswerIdsByInstanceGuidAndQuestionId(activityInstanceGuid, dto.getId()));
        }

        List<Rule> untypedRules = getValidationDao().getValidationRules(dto, langCodeId, instanceCreatedAtMillis);

        Question question;

        switch (dto.getType()) {
            case BOOLEAN:
                question = getBooleanQuestion((BooleanQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case PICKLIST:
                question = getPicklistQuestion((PicklistQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case MATRIX:
                question = getMatrixQuestion((MatrixQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case TEXT:
                question = getTextQuestion((TextQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case ACTIVITY_INSTANCE_SELECT:
                question = getActivityInstanceSelectQuestion((ActivityInstanceSelectQuestionDto) dto, activityInstanceGuid,
                        answerIds, untypedRules);
                break;
            case DATE:
                question = getDateQuestion((DateQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case FILE:
                question = getFileQuestion((FileQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case NUMERIC:
                question = getNumericQuestion((NumericQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case DECIMAL:
                question = getDecimalQuestion((DecimalQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case EQUATION:
                question = getEquationQuestion((EquationQuestionDto) dto, activityInstanceGuid);
                break;
            case AGREEMENT:
                question = getAgreementQuestion((AgreementQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case COMPOSITE:
                question = getCompositeQuestion((CompositeQuestionDto) dto,
                        activityInstanceGuid, instanceCreatedAtMillis, answerIds, untypedRules, langCodeId);
                break;
            default:
                throw new DaoException("Unknown question type: " + dto.getType());
        }

        question.setQuestionId(dto.getId());
        question.shouldHideQuestionNumber(dto.shouldHideNumber());
        return question;
    }

    /**
     * Build a boolean question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return boolean question object
     */
    default BoolQuestion getBooleanQuestion(BooleanQuestionDto dto,
                                            String activityInstanceGuid,
                                            List<Long> answerIds,
                                            List<Rule> untypedRules) {
        AnswerDao answerDao = getAnswerDao();
        List<BoolAnswer> boolAnswers = answerIds.stream()
                .map(answerId -> (BoolAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find boolean answer with id " + answerId)))
                .collect(toList());

        List<Rule<BoolAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<BoolAnswer>) rule)
                .collect(toList());

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);

        return new BoolQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.isRestricted(), dto.isDeprecated(), isReadonly, dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(), dto.getAdditionalInfoFooterTemplateId(),
                boolAnswers, rules, dto.getTrueTemplateId(),
                dto.getFalseTemplateId(), dto.getRenderMode());
    }

    /**
     * Build a picklist question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return picklist question object
     */
    default PicklistQuestion getPicklistQuestion(PicklistQuestionDto dto,
                                                 String activityInstanceGuid,
                                                 List<Long> answerIds,
                                                 List<Rule> untypedRules) {
        AnswerDao answerDao = getAnswerDao();
        List<PicklistAnswer> picklistAnswers = answerIds.stream()
                .map(answerId -> (PicklistAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find picklist answer with id " + answerId)))
                .collect(toList());

        List<Rule<PicklistAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<PicklistAnswer>) rule)
                .collect(toList());

        ActivityInstanceDto instanceDto = getHandle().attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(activityInstanceGuid)
                .orElseThrow(() -> new DaoException("Could not find activity instance using guid " + activityInstanceGuid
                        + " while getting picklist question " + dto.getStableId()));
        ActivityVersionDto versionDto = ActivityDefStore.getInstance()
                .findVersionDto(getHandle(), instanceDto.getActivityId(), instanceDto.getCreatedAtMillis())
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find activity version for instance with guid=%s using"
                                + " activityId=%d and createdAt=%d while getting picklist question %s",
                        activityInstanceGuid, instanceDto.getActivityId(), instanceDto.getCreatedAtMillis(), dto.getStableId())));

        PicklistQuestionDao.GroupAndOptionDtos container = getPicklistQuestionDao()
                .findOrderedGroupAndOptionDtos(dto.getId(), versionDto.getRevStart());

        List<PicklistGroup> groups = new ArrayList<>();
        List<PicklistOption> allOptions = new ArrayList<>();

        PicklistOption option = null;
        // Put the ungrouped options in the list first.
        for (PicklistOptionDto optionDto : container.getUngroupedOptions()) {
            if (CollectionUtils.isEmpty(optionDto.getNestedOptions())) {
                option = new PicklistOption(optionDto.getStableId(),
                        optionDto.getOptionLabelTemplateId(), optionDto.getTooltipTemplateId(), optionDto.getDetailLabelTemplateId(),
                        optionDto.isAllowDetails(), optionDto.isExclusive(), optionDto.isDefault());
            } else {
                //add nested options
                List<PicklistOption> nestedOptions = new ArrayList<>();
                optionDto.getNestedOptions().forEach(nestedOptionDto -> {
                    nestedOptions.add(new PicklistOption(nestedOptionDto.getStableId(),
                            nestedOptionDto.getOptionLabelTemplateId(), nestedOptionDto.getTooltipTemplateId(),
                            nestedOptionDto.getDetailLabelTemplateId(), nestedOptionDto.isAllowDetails(),
                            nestedOptionDto.isExclusive(), nestedOptionDto.isDefault()));
                });

                option = new PicklistOption(optionDto.getStableId(),
                        optionDto.getOptionLabelTemplateId(), optionDto.getTooltipTemplateId(), optionDto.getDetailLabelTemplateId(),
                        optionDto.isAllowDetails(), optionDto.isExclusive(), optionDto.isDefault(), optionDto.getNestedOptionsTemplateId(),
                        nestedOptions);
            }
            allOptions.add(option);
        }

        // Then options from groups.
        for (PicklistGroupDto groupDto : container.getGroups()) {
            groups.add(new PicklistGroup(groupDto.getStableId(), groupDto.getNameTemplateId()));
            List<PicklistOptionDto> optionDtos = container.getGroupIdToOptions().get(groupDto.getId());
            for (PicklistOptionDto optionDto : optionDtos) {
                allOptions.add(new PicklistOption(groupDto.getStableId(), optionDto.getStableId(),
                        optionDto.getOptionLabelTemplateId(), optionDto.getTooltipTemplateId(), optionDto.getDetailLabelTemplateId(),
                        optionDto.isAllowDetails(), optionDto.isExclusive(), optionDto.isDefault()));
            }
        }

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);

        return new PicklistQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.isRestricted(), dto.isDeprecated(), isReadonly, dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(), dto.getAdditionalInfoFooterTemplateId(),
                picklistAnswers, rules,
                dto.getSelectMode(),
                dto.getRenderMode(),
                dto.getLabelTemplateId(),
                allOptions, groups);
    }

    /**
     * Build a matrix question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return matrix question object
     */
    default MatrixQuestion getMatrixQuestion(MatrixQuestionDto dto,
                                             String activityInstanceGuid,
                                             List<Long> answerIds,
                                             List<Rule> untypedRules) {
        AnswerDao answerDao = getAnswerDao();
        List<MatrixAnswer> picklistAnswers = answerIds.stream()
                .map(answerId -> (MatrixAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find matrix answer with id " + answerId)))
                .collect(toList());

        List<Rule<MatrixAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<MatrixAnswer>) rule)
                .collect(toList());

        ActivityInstanceDto instanceDto = getHandle().attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(activityInstanceGuid)
                .orElseThrow(() -> new DaoException("Could not find activity instance using guid " + activityInstanceGuid
                        + " while getting matrix question " + dto.getStableId()));
        ActivityVersionDto versionDto = ActivityDefStore.getInstance()
                .findVersionDto(getHandle(), instanceDto.getActivityId(), instanceDto.getCreatedAtMillis())
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find activity version for instance with guid=%s using"
                                + " activityId=%d and createdAt=%d while getting picklist question %s",
                        activityInstanceGuid, instanceDto.getActivityId(), instanceDto.getCreatedAtMillis(), dto.getStableId())));

        MatrixQuestionDao.GroupOptionRowDtos container = getMatrixQuestionDao()
                .findOrderedGroupOptionRowDtos(dto.getId(), versionDto.getRevStart());

        List<MatrixGroup> groups = new ArrayList<>();
        List<MatrixOption> options = new ArrayList<>();
        List<MatrixRow> questions = new ArrayList<>();

        for (MatrixOptionDto optionDto : container.getOptions()) {
            options.add(new MatrixOption(optionDto.getStableId(), optionDto.getOptionLabelTemplateId(),
                    optionDto.getTooltipTemplateId(),
                    getJdbiMatrixGroup().findGroupCodeById(optionDto.getGroupId()),
                    optionDto.isExclusive()));
        }

        for (MatrixRowDto questionDto : container.getRows()) {
            questions.add(new MatrixRow(questionDto.getStableId(), questionDto.getQuestionLabelTemplateId(),
                    questionDto.getTooltipTemplateId()));
        }

        for (MatrixGroupDto groupDto : container.getGroups()) {
            if (groupDto.getStableId() == null) {
                continue;
            }
            groups.add(new MatrixGroup(groupDto.getStableId(), groupDto.getNameTemplateId()));
        }

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);

        return new MatrixQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.isRestricted(), dto.isRenderModal(), dto.isDeprecated(), isReadonly, dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(), dto.getAdditionalInfoFooterTemplateId(),
                dto.getModalTemplateId(), dto.getModalTitleTemplateId(), picklistAnswers,
                rules, dto.getSelectMode(), groups, options, questions);
    }

    /**
     * Build a text question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return text question object
     */
    default TextQuestion getTextQuestion(TextQuestionDto dto,
                                         String activityInstanceGuid,
                                         List<Long> answerIds,
                                         List<Rule> untypedRules) {
        AnswerDao answerDao = getAnswerDao();
        List<TextAnswer> textAnswers = answerIds.stream()
                .map(answerId -> (TextAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find text answer with id " + answerId)))
                .collect(toList());

        List<Rule<TextAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<TextAnswer>) rule)
                .collect(toList());

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);

        List<String> suggestions = new ArrayList<>();
        if (dto.getSuggestionType() == SuggestionType.INCLUDED) {
            suggestions = getJdbiQuestion().findTextQuestionSuggestions(dto.getId());
        }

        return new TextQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.getPlaceholderTemplateId(),
                dto.getConfirmPlaceholderTemplateId(),
                dto.isRestricted(),
                dto.isDeprecated(),
                isReadonly,
                dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(),
                dto.getAdditionalInfoFooterTemplateId(),
                textAnswers,
                rules,
                dto.getInputType(),
                dto.getSuggestionType(),
                suggestions,
                dto.isConfirmEntry(),
                dto.getConfirmPromptTemplateId(),
                dto.getMismatchMessageTemplateId());
    }

    /**
     * Build an activity instance select question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return activity instance select question object
     */
    default ActivityInstanceSelectQuestion getActivityInstanceSelectQuestion(ActivityInstanceSelectQuestionDto dto,
                                                                             String activityInstanceGuid,
                                                                             List<Long> answerIds,
                                                                             List<Rule> untypedRules) {
        AnswerDao answerDao = getAnswerDao();
        List<ActivityInstanceSelectAnswer> activityInstanceSelectAnswers = answerIds.stream()
                .map(answerId -> (ActivityInstanceSelectAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find the Activity Instance Select answer with id "
                                + answerId)))
                .collect(toList());

        List<Rule<ActivityInstanceSelectAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<ActivityInstanceSelectAnswer>) rule)
                .collect(toList());

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);
        List<String> activityCodes = getJdbiQuestion().getActivityCodesByActivityInstanceSelectQuestionId(dto.getId());

        return new ActivityInstanceSelectQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.isRestricted(),
                dto.isDeprecated(),
                isReadonly,
                dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(),
                dto.getAdditionalInfoFooterTemplateId(),
                activityInstanceSelectAnswers,
                rules,
                activityCodes);
    }

    /**
     * Build a date question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return date question object
     */
    default DateQuestion getDateQuestion(DateQuestionDto dto,
                                         String activityInstanceGuid,
                                         List<Long> answerIds,
                                         List<Rule> untypedRules) {
        AnswerDao answerDao = getAnswerDao();
        List<DateAnswer> dateAnswers = answerIds.stream()
                .map(answerId -> (DateAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find date answer with id " + answerId)))
                .collect(toList());

        List<Rule<DateAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<DateAnswer>) rule)
                .collect(toList());

        List<DateFieldType> fieldTypes = dto.getFields();
        if (fieldTypes.isEmpty()) {
            throw new DaoException("Date question should have at least one date field but none found for question id "
                    + dto.getId());
        }

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);

        if (dto.getRenderMode().equals(DateRenderMode.PICKLIST)) {
            DatePicklistDef config = dto.getPicklistDef();
            return new DatePicklistQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                    dto.isRestricted(), dto.isDeprecated(), isReadonly, dto.getTooltipTemplateId(),
                    dto.getAdditionalInfoHeaderTemplateId(), dto.getAdditionalInfoFooterTemplateId(),
                    dateAnswers, rules, dto.getRenderMode(), dto.shouldDisplayCalendar(),
                    fieldTypes, dto.getPlaceholderTemplateId(), config.getUseMonthNames(), config.getStartYear(),
                    config.getEndYear(), config.getFirstSelectedYear());
        } else {
            return new DateQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                    dto.isRestricted(), dto.isDeprecated(), isReadonly,
                    dto.getTooltipTemplateId(),
                    dto.getAdditionalInfoHeaderTemplateId(),
                    dto.getAdditionalInfoFooterTemplateId(),
                    dateAnswers,
                    rules,
                    dto.getRenderMode(),
                    dto.shouldDisplayCalendar(),
                    fieldTypes,
                    dto.getPlaceholderTemplateId());
        }
    }

    /**
     * Build a file question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return file question object
     */
    default Question getFileQuestion(FileQuestionDto dto, String activityInstanceGuid,
                                     List<Long> answerIds, List<Rule> untypedRules) {
        AnswerDao answerDao = getAnswerDao();
        List<FileAnswer> answers = answerIds.stream()
                .map(answerId -> (FileAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find file answer with id " + answerId)))
                .collect(toList());

        List<Rule<FileAnswer>> rules = untypedRules.stream()
                .map(rule -> (Rule<FileAnswer>) rule)
                .collect(toList());

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);

        return new FileQuestion(
                dto.getStableId(),
                dto.getPromptTemplateId(),
                dto.isRestricted(),
                dto.isDeprecated(),
                isReadonly,
                dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(),
                dto.getAdditionalInfoFooterTemplateId(),
                answers,
                rules,
                dto.getMaxFileSize(),
                dto.getMimeTypes());
    }

    /**
     * Build a numeric question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return numeric question object
     */
    default Question getNumericQuestion(NumericQuestionDto dto, String activityInstanceGuid,
                                        List<Long> answerIds, List<Rule> untypedRules) {
        AnswerDao answerDao = getAnswerDao();
        List<NumericAnswer> answers = answerIds.stream()
                .map(answerId -> (NumericAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find numeric answer with id " + answerId)))
                .collect(toList());

        List<Rule<NumericAnswer>> rules = untypedRules.stream()
                .map(rule -> (Rule<NumericAnswer>) rule)
                .collect(toList());

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);

        return new NumericQuestion(
                dto.getStableId(),
                dto.getPromptTemplateId(),
                dto.getPlaceholderTemplateId(),
                dto.isRestricted(),
                dto.isDeprecated(),
                isReadonly,
                dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(),
                dto.getAdditionalInfoFooterTemplateId(),
                answers,
                rules);
    }

    /**
     * Build a decimal question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return numeric question object
     */
    default Question getDecimalQuestion(DecimalQuestionDto dto, String activityInstanceGuid,
                                        List<Long> answerIds, List<Rule> untypedRules) {
        AnswerDao answerDao = getAnswerDao();
        List<DecimalAnswer> answers = answerIds.stream()
                .map(answerId -> (DecimalAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find decimal answer with id " + answerId)))
                .collect(toList());

        List<Rule<DecimalAnswer>> rules = untypedRules.stream()
                .map(rule -> (Rule<DecimalAnswer>) rule)
                .collect(toList());

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);

        return new DecimalQuestion(
                dto.getStableId(),
                dto.getPromptTemplateId(),
                dto.getPlaceholderTemplateId(),
                dto.isRestricted(),
                dto.isDeprecated(),
                isReadonly,
                dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(),
                dto.getAdditionalInfoFooterTemplateId(),
                answers,
                rules,
                dto.getScale());
    }

    /**
     * Build a decimal question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @return numeric question object
     */
    default Question getEquationQuestion(EquationQuestionDto dto, String activityInstanceGuid) {
        var questionEvaluator = new QuestionEvaluator(getHandle(), activityInstanceGuid);

        return new EquationQuestion(
                dto.getStableId(),
                dto.getPromptTemplateId(),
                dto.getPlaceholderTemplateId(),
                dto.isRestricted(),
                dto.isDeprecated(),
                dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(),
                dto.getAdditionalInfoFooterTemplateId(),
                StreamEx.of(getJdbiEquationQuestion().findEquationsByActivityInstanceGuid(activityInstanceGuid))
                        .filterBy(EquationQuestionDto::getStableId, dto.getStableId())
                        .map(questionEvaluator::evaluate)
                        .filter(Objects::nonNull)
                        .map(EquationAnswer::new)
                        .toList(),
                Collections.emptyList(),
                dto.getMaximumDecimalPlaces(),
                dto.getExpression());
    }

    /**
     * Build a agreement question.
     *
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return agreement question object
     */
    default AgreementQuestion getAgreementQuestion(AgreementQuestionDto dto,
                                                   String activityInstanceGuid,
                                                   List<Long> answerIds,
                                                   List<Rule> untypedRules) {
        AnswerDao answerDao = getAnswerDao();
        List<AgreementAnswer> agreementAnswers = answerIds.stream()
                .map(answerId -> (AgreementAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find agreement answer with id " + answerId)))
                .collect(toList());

        List<Rule<AgreementAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<AgreementAnswer>) rule)
                .collect(toList());

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);

        return new AgreementQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.isRestricted(), dto.isDeprecated(), isReadonly, dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(), dto.getAdditionalInfoFooterTemplateId(),
                agreementAnswers, rules);
    }

    /**
     * Build a composite question.
     *
     * @param dto                     the question dto
     * @param activityInstanceGuid    the activity instance guid
     * @param instanceCreatedAtMillis the timestamp of when instance was created
     * @param answerIds               list of base answer ids to question (may be empty)
     * @param untypedRules            list of untyped validations for question (may be empty)
     * @return composite question object
     */
    default CompositeQuestion getCompositeQuestion(CompositeQuestionDto dto,
                                                   String activityInstanceGuid,
                                                   long instanceCreatedAtMillis,
                                                   List<Long> answerIds,
                                                   List<Rule> untypedRules,
                                                   long langCodeId) {
        JdbiQuestion jdbiQuestion = getJdbiQuestion();
        List<Long> childIds = jdbiQuestion.findCompositeChildIdsByParentIdAndInstanceGuid(dto.getId(), activityInstanceGuid);
        List<Question> childQuestions;
        try (var stream = jdbiQuestion.findQuestionDtosByIds(childIds)) {
            childQuestions = stream
                    .map(childQuestionDto ->
                            getQuestionByActivityInstanceAndDto(childQuestionDto, activityInstanceGuid,
                                    instanceCreatedAtMillis, false, langCodeId))
                    .collect(toList());
        }

        AnswerDao answerDao = getAnswerDao();
        List<CompositeAnswer> compositeAnswers = answerIds.stream()
                .map(answerId -> (CompositeAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find composite answer with id " + answerId)))
                .collect(toList());
        LOG.info("Found {} answers for question {} in form instance {}",
                answerIds.size(), dto.getStableId(), activityInstanceGuid);

        List<Rule<CompositeAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<CompositeAnswer>) rule)
                .collect(toList());

        boolean isReadonly = QuestionUtil.isReadonly(getHandle(), dto, activityInstanceGuid);

        return new CompositeQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.isRestricted(), dto.isDeprecated(), isReadonly, dto.getTooltipTemplateId(),
                dto.getAdditionalInfoHeaderTemplateId(), dto.getAdditionalInfoFooterTemplateId(),
                rules, dto.isAllowMultiple(), dto.isUnwrapOnExport(),
                dto.getAddButtonTemplateId(), dto.getAdditionalItemTemplateId(),
                childQuestions, dto.getChildOrientation(), dto.getTabularSeparator(), compositeAnswers);
    }


    /**
     * Create new question block by inserting question data and associating it to its block.
     *
     * @param activityId    the associated activity
     * @param questionBlock the question block definition, with the block id
     * @param revisionId    the revision to use, will be shared by all created data
     */
    default void insertQuestionBlock(long activityId, QuestionBlockDef questionBlock, long revisionId) {
        insertQuestionByType(activityId, questionBlock.getQuestion(), revisionId);
        getJdbiBlockQuestion().insert(questionBlock.getBlockId(), questionBlock.getQuestion().getQuestionId());
    }

    /**
     * Delete an "orphan" question that has no answers and is not reference by a block
     * Added for purpose of supporting tests and studybuilder patch operations.
     * Note: Implementation is incomplete at this time (August 27 2022) and only supports deletion of question types
     * that were needed at time.
     * @param questionDef question to delete
     *
     * @throws org.broadinstitute.ddp.exception.DDPException if deletion failed for some reason
     */
    default void deleteQuestion(QuestionDef questionDef) {
        Long questionId = questionDef.getQuestionId();
        switch (questionDef.getQuestionType()) {
            case TEXT:
                deleteTextQuestion(questionId);
                break;
            case PICKLIST:
                getPicklistQuestionDao().delete(questionId);
                break;
            case COMPOSITE:
                deleteCompositeQuestion((CompositeQuestionDef) questionDef, questionId);
                break;
            case DATE:
                deleteDateQuestion(questionId);
                break;
            default:
                throw new DDPException("Deletion of questions of type: " + questionDef.getQuestionType()
                        + " is not supported. Consider implementing it");
        }
        deleteBaseQuestion(questionId);
    }

    private void deleteTextQuestion(Long questionId) {
        var wasDeleted = getJdbiTextQuestion().delete(questionId);
        LOG.info("Deleted text question with id {}?: {}", questionId, wasDeleted);
    }

    private void deleteCompositeQuestion(CompositeQuestionDef questionDef, Long questionId) {
        getJdbiCompositeQuestion().deleteChildQuestionMembership(questionId);
        var wasDeleted =  getJdbiCompositeQuestion().deleteCompositeQuestionParentRecord(questionId);
        LOG.info("Deleted parent composite question with id {}?: {}", questionId, wasDeleted);
        questionDef.getChildren().forEach(child -> deleteQuestion(child));
    }

    default boolean deleteBaseQuestion(long questionId) {
        getJdbiQuestionValidation().deleteForQuestion(questionId);

        return getJdbiQuestion().deleteBaseQuestion(questionId);
    }

    /**
     * Create new question by looking at its type.
     *
     * @param activityId the associated activity
     * @param question   the question block definition, where only question data is used
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertQuestionByType(long activityId, QuestionDef question, long revisionId) {
        QuestionType questionType = question.getQuestionType();
        switch (questionType) {
            case BOOLEAN:
                insertQuestion(activityId, (BoolQuestionDef) question, revisionId);
                break;
            case TEXT:
                insertQuestion(activityId, (TextQuestionDef) question, revisionId);
                break;
            case ACTIVITY_INSTANCE_SELECT:
                insertQuestion(activityId, (ActivityInstanceSelectQuestionDef) question, revisionId);
                break;
            case DATE:
                insertQuestion(activityId, (DateQuestionDef) question, revisionId);
                break;
            case FILE:
                insertQuestion(activityId, (FileQuestionDef) question, revisionId);
                break;
            case NUMERIC:
                insertQuestion(activityId, (NumericQuestionDef) question, revisionId);
                break;
            case DECIMAL:
                insertQuestion(activityId, (DecimalQuestionDef) question, revisionId);
                break;
            case EQUATION:
                insertQuestion(activityId, (EquationQuestionDef) question, revisionId);
                break;
            case PICKLIST:
                insertQuestion(activityId, (PicklistQuestionDef) question, revisionId);
                break;
            case MATRIX:
                insertQuestion(activityId, (MatrixQuestionDef) question, revisionId);
                break;
            case COMPOSITE:
                insertQuestion(activityId, (CompositeQuestionDef) question, revisionId);
                break;
            case AGREEMENT:
                insertQuestion(activityId, (AgreementQuestionDef) question, revisionId);
                break;
            default:
                throw new DaoException("Unhandled question type " + questionType);
        }
    }

    /**
     * End currently active question block by finding its associated question and terminating all its data.
     *
     * @param blockId the associated block
     * @param meta    the revision metadata used for terminating data
     */
    default void disableQuestionBlock(long blockId, RevisionMetadata meta) {
        JdbiBlockQuestion jdbiBlockQuestion = getJdbiBlockQuestion();
        TypedQuestionId qid = jdbiBlockQuestion.getActiveTypedQuestionId(blockId).orElseThrow(() ->
                new NoSuchElementException("Cannot find active question for block " + blockId));
        switch (qid.getType()) {
            case BOOLEAN:
                disableBoolQuestion(qid.getId(), meta);
                break;
            case TEXT:
                disableTextQuestion(qid.getId(), meta);
                break;
            case ACTIVITY_INSTANCE_SELECT:
                disableActivityInstanceSelectQuestion(qid.getId(), meta);
                break;
            case DATE:
                disableDateQuestion(qid.getId(), meta);
                break;
            case FILE:
                disableFileQuestion(qid.getId(), meta);
                break;
            case NUMERIC:
                disableNumericQuestion(qid.getId(), meta);
                break;
            case DECIMAL:
                disableDecimalQuestion(qid.getId(), meta);
                break;
            case EQUATION:
                disableEquationQuestion(qid.getId(), meta);
                break;
            case PICKLIST:
                disablePicklistQuestion(qid.getId(), meta);
                break;
            case MATRIX:
                disableMatrixQuestion(qid.getId(), meta);
                break;
            case AGREEMENT:
                disableAgreementQuestion(qid.getId(), meta);
                break;
            default:
                throw new DaoException("Unhandled question type " + qid.getType());
        }
    }

    /**
     * Create the basic question by inserting the common data, and then the validation rules.
     *
     * <p>Note: clients should not call this method directly but should instead go through the other question specific
     * insertion methods.
     *
     * @param activityId the associated activity
     * @param question   the question definition, without generated things like ids
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertBaseQuestion(long activityId, QuestionDef question, long revisionId) {
        if (question.getQuestionId() != null) {
            throw new IllegalStateException("Question id already set to " + question.getQuestionId());
        }

        JdbiQuestion jdbiQuestion = getJdbiQuestion();
        JdbiQuestionType jdbiQuestionType = getJdbiQuestionType();

        long studyId = getJdbiActivity().getStudyIdByActivityId(activityId).orElseThrow(() ->
                new NoSuchElementException("Cannot find study id using activity id " + activityId));
        long stableCodeId = getJdbiQuestionStableCode().insertStableId(studyId, question.getStableId());

        TemplateDao templateDao = getTemplateDao();
        templateDao.insertTemplate(question.getPromptTemplate(), revisionId);

        Long tooltipTemplateId = null;
        if (question.getTooltipTemplate() != null) {
            if (question.getTooltipTemplate().getTemplateType() != TemplateType.TEXT) {
                throw new DaoException("Only TEXT template type is supported for tooltips");
            }
            tooltipTemplateId = templateDao.insertTemplate(question.getTooltipTemplate(), revisionId);
        }

        Template footerTemplate = question.getAdditionalInfoFooterTemplate();
        Long footerTemplateId = null;
        if (footerTemplate != null) {
            templateDao.insertTemplate(footerTemplate, revisionId);
            footerTemplateId = footerTemplate.getTemplateId();
        }

        Template headerTemplate = question.getAdditionalInfoHeaderTemplate();
        Long headerTemplateId = null;
        if (headerTemplate != null) {
            templateDao.insertTemplate(headerTemplate, revisionId);
            headerTemplateId = headerTemplate.getTemplateId();
        }

        long questionId = jdbiQuestion.insert(
                jdbiQuestionType.getTypeId(question.getQuestionType()),
                question.isRestricted(),
                stableCodeId,
                question.getPromptTemplate().getTemplateId(),
                tooltipTemplateId,
                headerTemplateId,
                footerTemplateId,
                revisionId, activityId, question.shouldHideNumber(),
                question.isDeprecated(),
                question.isWriteOnce());
        question.setQuestionId(questionId);

        getValidationDao().insertValidations(question, revisionId);
        LOG.info("Inserted {} validations for {}", question.getValidations().size(), question.getStableId());
    }

    /**
     * End currently active question by terminating its common data, then the validation rule templates.
     *
     * <p>Note: clients should not call this method directly but should instead go through the other question specific
     * methods.
     *
     * @param question the question data
     * @param meta     the revision metadata used for terminating data
     */
    default void disableBaseQuestion(QuestionDto question, RevisionMetadata meta) {
        JdbiRevision jdbiRev = getJdbiRevision();
        JdbiQuestion jdbiQuestion = getJdbiQuestion();
        TemplateDao tmplDao = getTemplateDao();

        if (question.getTooltipTemplateId() != null) {
            tmplDao.disableTemplate(question.getTooltipTemplateId(), meta);
        }

        tmplDao.disableTemplate(question.getPromptTemplateId(), meta);
        long newRevId = jdbiRev.copyAndTerminate(question.getRevisionId(), meta);
        int numUpdated = jdbiQuestion.updateRevisionIdById(question.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DaoException("Unable to terminate active question " + question.getId());
        }
        if (jdbiRev.tryDeleteOrphanedRevision(question.getRevisionId())) {
            LOG.info("Deleted orphaned revision {} by question {}", question.getRevisionId(), question.getId());
        }

        getValidationDao().disableValidations(question.getId(), meta);
    }

    /**
     * Create new boolean question by inserting common data and boolean specific data.
     *
     * @param activityId   the associated activity
     * @param boolQuestion the question definition, without generated things like ids
     * @param revisionId   the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, BoolQuestionDef boolQuestion, long revisionId) {
        insertBaseQuestion(activityId, boolQuestion, revisionId);

        TemplateDao templateDao = getTemplateDao();
        templateDao.insertTemplate(boolQuestion.getTrueTemplate(), revisionId);
        templateDao.insertTemplate(boolQuestion.getFalseTemplate(), revisionId);

        int numInserted = getJdbiBooleanQuestion().insert(boolQuestion.getQuestionId(),
                boolQuestion.getTrueTemplate().getTemplateId(), boolQuestion.getFalseTemplate().getTemplateId(),
                boolQuestion.getRenderMode());
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for bool question " + boolQuestion.getStableId());
        }
    }

    /**
     * Create new text question by inserting common data and text specific data.
     *
     * @param activityId   the associated activity
     * @param textQuestion the question definition, without generated things like ids
     * @param revisionId   the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, TextQuestionDef textQuestion, long revisionId) {
        insertBaseQuestion(activityId, textQuestion, revisionId);
        TemplateDao templateDao = getTemplateDao();

        Long placeholderTemplateId = templateDao.insertTemplateIfNotNull(textQuestion.getPlaceholderTemplate(), revisionId);
        Long confirmPlaceholderTemplateId = templateDao.insertTemplateIfNotNull(textQuestion.getConfirmPlaceholderTemplate(), revisionId);
        Long confirmEntryTemplateId = templateDao.insertTemplateIfNotNull(textQuestion.getConfirmPromptTemplate(), revisionId);
        Long mismatchMessageTemplateId = templateDao.insertTemplateIfNotNull(textQuestion.getMismatchMessageTemplate(), revisionId);

        int numInserted = getJdbiTextQuestion().insert(textQuestion.getQuestionId(),
                textQuestion.getInputType(),
                textQuestion.getSuggestionType(),
                placeholderTemplateId,
                confirmPlaceholderTemplateId,
                textQuestion.isConfirmEntry(),
                confirmEntryTemplateId,
                mismatchMessageTemplateId);
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for text question " + textQuestion.getStableId());
        }

        if (CollectionUtils.isNotEmpty(textQuestion.getSuggestions())) {
            int[] ids = getJdbiTextQuestionSuggestion().insert(textQuestion.getQuestionId(), textQuestion.getSuggestions(),
                    Stream.iterate(0, i -> i + DISPLAY_ORDER_GAP).iterator());
            if (ids.length != textQuestion.getSuggestions().size()) {
                throw new DaoException("Inserted " + numInserted + " suggestions" + textQuestion.getStableId());
            }
        }
    }

    /**
     * Create new activity instance select question by inserting common data and text specific data.
     *
     * @param activityId                     the associated activity
     * @param activityInstanceSelectQuestion the question definition, without generated things like ids
     * @param revisionId                     the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, ActivityInstanceSelectQuestionDef activityInstanceSelectQuestion,
                                long revisionId) {
        insertBaseQuestion(activityId, activityInstanceSelectQuestion, revisionId);

        int numInserted = getJdbiActivityInstanceSelectQuestion().insert(activityInstanceSelectQuestion.getQuestionId());

        if (numInserted != 1) {
            throw new DaoException("Expected 1 activity instance select question with stableId "
                    + activityInstanceSelectQuestion.getStableId()
                    + " to be inserted, but " + numInserted + " inserted instead");
        }

        if (CollectionUtils.isNotEmpty(activityInstanceSelectQuestion.getActivityCodes())) {
            int[] ids = getJdbiActivityInstanceSelectActivityCodes().insert(activityInstanceSelectQuestion.getQuestionId(),
                    activityInstanceSelectQuestion.getActivityCodes(),
                    Stream.iterate(0, i -> i + DISPLAY_ORDER_GAP).iterator());
            if (ids.length != activityInstanceSelectQuestion.getActivityCodes().size()) {
                throw new DaoException("Expected " + activityInstanceSelectQuestion.getActivityCodes().size()
                        + " activity codes to be inserted for activity instance select questions with stableId "
                        + activityInstanceSelectQuestion.getStableId()
                        + " but " + ids.length + " inserted instead");
            }
        }
    }

    /**
     * Create new date question by inserting common data and date specific data. If question has definition for the
     * picklist dropdowns, those data will be inserted.
     *
     * @param activityId   the associated activity
     * @param dateQuestion the question definition, without generated things like ids
     * @param revisionId   the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, DateQuestionDef dateQuestion, long revisionId) {
        insertBaseQuestion(activityId, dateQuestion, revisionId);

        JdbiDateQuestionFieldOrder jdbiDateQuestionFieldOrder = getJdbiDateQuestionFieldOrder();
        JdbiDateFieldType jdbiDateFieldType = getJdbiDateFieldType();
        TemplateDao templateDao = getTemplateDao();

        Long placeholderTemplateId = null;
        if (dateQuestion.getPlaceholderTemplate() != null) {
            placeholderTemplateId = templateDao.insertTemplate(dateQuestion.getPlaceholderTemplate(), revisionId);
        }

        int numInserted = getJdbiDateQuestion().insert(
                dateQuestion.getQuestionId(),
                getJdbiDateRenderMode().getModeId(dateQuestion.getRenderMode()),
                dateQuestion.isDisplayCalendar(),
                placeholderTemplateId);
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for date question " + dateQuestion.getStableId());
        }

        List<DateFieldType> fields = dateQuestion.getFields();
        int fieldOrder = 0;

        for (DateFieldType dateField : fields) {
            jdbiDateQuestionFieldOrder.insert(dateQuestion.getQuestionId(),
                    jdbiDateFieldType.getTypeId(dateField), fieldOrder++);
        }

        if (DateRenderMode.PICKLIST.equals(dateQuestion.getRenderMode()) && dateQuestion.getPicklistDef() != null) {
            DatePicklistDef config = dateQuestion.getPicklistDef();

            if (fields.contains(DateFieldType.MONTH)) {
                boolean useName = config.getUseMonthNames() == null
                        ? DatePicklistDef.DEFAULT_USE_MONTH_NAMES : config.getUseMonthNames();
                numInserted = getJdbiDateQuestionMonthPicklist().insert(dateQuestion.getQuestionId(), useName);
                if (numInserted != 1) {
                    throw new DaoException("Inserted " + numInserted + " for date question "
                            + dateQuestion.getStableId() + " month picklist");
                }
            }

            if (fields.contains(DateFieldType.YEAR)) {
                numInserted = getJdbiDateQuestionYearPicklist().insert(
                        dateQuestion.getQuestionId(),
                        config.getYearsForward(),
                        config.getYearsBack(),
                        config.getYearAnchor(),
                        config.getFirstSelectedYear(),
                        config.shouldAllowFutureYears());
                if (numInserted != 1) {
                    throw new DaoException("Inserted " + numInserted + " for date question "
                            + dateQuestion.getStableId() + " year picklist");
                }
            }
        }
    }

    /**
     * Create new file question by inserting common data and file question specific data.
     *
     * @param activityId   the associated activity
     * @param fileQuestion the file question definition, without generated things like ids
     * @param revisionId   the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, FileQuestionDef fileQuestion, long revisionId) {
        insertBaseQuestion(activityId, fileQuestion, revisionId);
        FileUploadValidator.validateFileMaxSize(fileQuestion.getMaxFileSize());
        JdbiQuestion jdbiQuestion = getJdbiQuestion();
        jdbiQuestion.insertFileQuestion(fileQuestion.getQuestionId(), fileQuestion.getMaxFileSize());
        Collection<String> mimeTypes = fileQuestion.getMimeTypes();
        for (String mimeType : mimeTypes) {
            long mimeTypeId = jdbiQuestion.findMimeTypeIdOrInsert(mimeType);
            jdbiQuestion.insertFileQuestionMimeType(fileQuestion.getQuestionId(), mimeTypeId);
        }
    }

    /**
     * Create new numeric question by inserting common data and numeric specific data.
     *
     * @param activityId  the associated activity
     * @param questionDef the question definition, without generated things like ids
     * @param revisionId  the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, NumericQuestionDef questionDef, long revisionId) {
        insertBaseQuestion(activityId, questionDef, revisionId);

        TemplateDao templateDao = getTemplateDao();
        Long placeholderTemplateId = null;
        if (questionDef.getPlaceholderTemplate() != null) {
            placeholderTemplateId = templateDao.insertTemplate(questionDef.getPlaceholderTemplate(), revisionId);
        }

        int numInserted = getJdbiNumericQuestion().insert(questionDef.getQuestionId(), placeholderTemplateId);
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for numeric question " + questionDef.getStableId());
        }
    }

    /**
     * Create new decimal question by inserting common data and numeric specific data.
     *
     * @param activityId  the associated activity
     * @param questionDef the question definition, without generated things like ids
     * @param revisionId  the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, DecimalQuestionDef questionDef, long revisionId) {
        insertBaseQuestion(activityId, questionDef, revisionId);

        TemplateDao templateDao = getTemplateDao();
        Long placeholderTemplateId = null;
        if (questionDef.getPlaceholderTemplate() != null) {
            placeholderTemplateId = templateDao.insertTemplate(questionDef.getPlaceholderTemplate(), revisionId);
        }

        int numInserted = getJdbiDecimalQuestion().insert(questionDef.getQuestionId(), placeholderTemplateId, questionDef.getScale());
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for decimal question " + questionDef.getStableId());
        }
    }

    /**
     * Create new equation question by inserting common data and numeric specific data.
     *
     * @param activityId  the associated activity
     * @param questionDef the question definition, without generated things like ids
     * @param revisionId  the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, EquationQuestionDef questionDef, long revisionId) {
        insertBaseQuestion(activityId, questionDef, revisionId);

        TemplateDao templateDao = getTemplateDao();
        Long placeholderTemplateId = null;
        if (questionDef.getPlaceholderTemplate() != null) {
            placeholderTemplateId = templateDao.insertTemplate(questionDef.getPlaceholderTemplate(), revisionId);
        }

        int numInserted = getJdbiEquationQuestion().insert(questionDef.getQuestionId(), placeholderTemplateId,
                questionDef.getMaximumDecimalPlaces(), questionDef.getExpression());
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for equation question " + questionDef.getStableId());
        }
    }

    /**
     * Create new matrix question by inserting common data and matrix specific data.
     *
     * @param activityId the associated activity
     * @param matrix     the question definition, without generated things like ids
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, MatrixQuestionDef matrix, long revisionId) {

        if (matrix.getRows().isEmpty() || matrix.getOptions().isEmpty()) {
            throw new IllegalStateException(String.format(
                    "matrix question %s need to have at least one option and one question",
                    matrix.getStableId()));
        }

        insertBaseQuestion(activityId, matrix, revisionId);

        TemplateDao templateDao = getTemplateDao();
        Long modalTemplateId = null;
        if (matrix.getModalTemplate() != null) {
            modalTemplateId = templateDao.insertTemplate(matrix.getModalTemplate(), revisionId);
        }

        Long modalTitleTemplateId = null;
        if (matrix.getModalTitleTemplate() != null) {
            modalTitleTemplateId = templateDao.insertTemplate(matrix.getModalTitleTemplate(), revisionId);
        }

        int numInserted = getJdbiMatrixQuestion().insert(matrix.getQuestionId(), matrix.getSelectMode(),
                matrix.isRenderModal(), modalTemplateId, modalTitleTemplateId);

        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for picklist question " + matrix.getStableId());
        }

        Map<String, Long> groupMap = new HashMap<>();
        if (!matrix.getGroups().isEmpty()) {
            getMatrixQuestionDao().insertGroups(matrix.getQuestionId(), matrix.getGroups(), revisionId);
            groupMap = matrix.getGroups().stream().collect(toMap(MatrixGroupDef::getStableId, MatrixGroupDef::getGroupId));
        }

        getMatrixQuestionDao().insertOptions(matrix.getQuestionId(), matrix.getOptions(), groupMap, revisionId);
        getMatrixQuestionDao().insertRowsQuestions(matrix.getQuestionId(), matrix.getRows(), revisionId);
    }

    /**
     * Create new picklist question by inserting common data and picklist specific data.
     *
     * @param activityId the associated activity
     * @param picklist   the question definition, without generated things like ids
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, PicklistQuestionDef picklist, long revisionId) {

        if (picklist.getGroups().isEmpty() && picklist.getPicklistOptions().isEmpty()) {
            throw new IllegalStateException(String.format(
                    "picklist question %s need to have at least one option or one group",
                    picklist.getStableId()));
        }

        insertBaseQuestion(activityId, picklist, revisionId);

        TemplateDao templateDao = getTemplateDao();
        Long labelTemplateId = null;
        if (picklist.getPicklistLabelTemplate() != null) {
            templateDao.insertTemplate(picklist.getPicklistLabelTemplate(), revisionId);
            labelTemplateId = picklist.getPicklistLabelTemplate().getTemplateId();
        }

        int numInserted = getJdbiPicklistQuestion().insert(
                picklist.getQuestionId(), picklist.getSelectMode(), picklist.getRenderMode(), labelTemplateId);
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for picklist question " + picklist.getStableId());
        }

        getPicklistQuestionDao().insertGroups(picklist.getQuestionId(), picklist.getGroups(), revisionId);
        getPicklistQuestionDao().insertOptions(picklist.getQuestionId(),
                picklist.getPicklistOptions(), revisionId);
    }

    default void insertQuestion(long activityId, CompositeQuestionDef compositeQuestion, long revisionId) {
        if (!compositeQuestion.isAcceptable()) {
            throw new DaoException("Composite questions only support following types for children: "
                    + StreamEx.of(QuestionType.values())
                            .filter(QuestionType::isCompositional)
                            .joining());
        }

        insertBaseQuestion(activityId, compositeQuestion, revisionId);
        TemplateDao templateDao = getTemplateDao();
        Long buttonTemplateId = null;
        if (compositeQuestion.getAddButtonTemplate() != null) {
            buttonTemplateId = templateDao.insertTemplate(compositeQuestion.getAddButtonTemplate(), revisionId);
        }
        Long addItemTemplateId = null;
        if (compositeQuestion.getAdditionalItemTemplate() != null) {
            addItemTemplateId = templateDao.insertTemplate(compositeQuestion.getAdditionalItemTemplate(), revisionId);
        }
        getJdbiCompositeQuestion().insertParent(compositeQuestion.getQuestionId(), compositeQuestion.isAllowMultiple(),
                buttonTemplateId, addItemTemplateId, compositeQuestion.getChildOrientation(), compositeQuestion.isUnwrapOnExport(),
                compositeQuestion.getTabularSeparator());
        compositeQuestion.getChildren().forEach(childQuestion -> this.insertQuestionByType(activityId, childQuestion, revisionId));
        getJdbiCompositeQuestion().insertChildren(compositeQuestion.getQuestionId(), compositeQuestion.getChildren()
                .stream()
                .map(QuestionDef::getQuestionId).collect(toList()));
    }

    /**
     * Creates a new agreement question by inserting common data and agreement question-specific data
     *
     * @param activityId        the associated activity
     * @param agreementQuestion the agreement question definition , without generated things like ids
     * @param revisionId        the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, AgreementQuestionDef agreementQuestion, long revisionId) {
        boolean hasRequiredRule = agreementQuestion.getValidations().stream()
                .filter(v -> v instanceof RequiredRuleDef)
                .count() > 0;
        if (!hasRequiredRule) {
            String errMsg = "An error occurred while trying to add an agreement question to the "
                    + "activity with id  " + activityId + ": the agreement question with stable id "
                    + agreementQuestion.getStableId() + " does not have a REQUIRED validation rule attached";
            LOG.info(errMsg);
            throw new DaoException(errMsg);
        }
        insertBaseQuestion(activityId, agreementQuestion, revisionId);
        int numInserted = getJdbiAgreementQuestion().insert(agreementQuestion.getQuestionId());
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for agreement question " + agreementQuestion.getStableId());
        }
    }

    default boolean deleteDateQuestion(long questionId) {
        getJdbiDateQuestionFieldOrder().deleteForQuestionId(questionId);
        var val = getJdbiDateQuestion().delete(questionId);
        LOG.info("Deleted date question id {}? {}", questionId, val);
        return val;
    }

    /**
     * End currently active boolean question by terminating common data and boolean specific data.
     *
     * @param questionId the question id
     * @param meta       the revision metadata used for terminating data
     */
    default void disableBoolQuestion(long questionId, RevisionMetadata meta) {
        TemplateDao tmplDao = getTemplateDao();

        QuestionDto dto = getJdbiQuestion().findQuestionDtoById(questionId).orElse(null);
        BooleanQuestionDto booleanQuestion = dto == null ? null : (BooleanQuestionDto) dto;
        if (booleanQuestion == null || booleanQuestion.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active boolean question with id " + questionId);
        }
        disableBaseQuestion(booleanQuestion, meta);

        tmplDao.disableTemplate(booleanQuestion.getTrueTemplateId(), meta);
        tmplDao.disableTemplate(booleanQuestion.getFalseTemplateId(), meta);
    }

    /**
     * End currently active text question by terminating common data and text specific data.
     *
     * @param questionId the question id
     * @param meta       the revision metadata used for terminating data
     */
    default void disableTextQuestion(long questionId, RevisionMetadata meta) {
        QuestionDto dto = getJdbiQuestion().findQuestionDtoById(questionId).orElse(null);
        TextQuestionDto question = dto == null ? null : (TextQuestionDto) dto;
        if (question == null || question.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active text question with id " + questionId);
        }
        disableBaseQuestion(question, meta);
        TemplateDao tmplDao = getTemplateDao();

        Long placeholderTemplateId = question.getPlaceholderTemplateId();
        if (placeholderTemplateId != null) {
            tmplDao.disableTemplate(placeholderTemplateId, meta);
        }
        Long confirmPlaceholderTemplateId = question.getConfirmPlaceholderTemplateId();
        if (confirmPlaceholderTemplateId != null) {
            tmplDao.disableTemplate(confirmPlaceholderTemplateId, meta);
        }
    }

    /**
     * End currently active activity instance select question by terminating common data and text specific data.
     *
     * @param questionId the question id
     * @param meta       the revision metadata used for terminating data
     */
    default void disableActivityInstanceSelectQuestion(long questionId, RevisionMetadata meta) {
        QuestionDto dto = getJdbiQuestion().findQuestionDtoById(questionId).orElse(null);
        ActivityInstanceSelectQuestionDto question = dto == null ? null : (ActivityInstanceSelectQuestionDto) dto;
        if (question == null || question.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active text question with id " + questionId);
        }
        disableBaseQuestion(question, meta);
    }

    /**
     * End currently active date question by terminating common data and date specific data.
     *
     * @param questionId the question id
     * @param meta       the revision metadata used for terminating data
     */
    default void disableDateQuestion(long questionId, RevisionMetadata meta) {
        QuestionDto question = getJdbiQuestion().findQuestionDtoById(questionId).orElse(null);
        if (question == null || question.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active date question with id " + questionId);
        }
        disableBaseQuestion(question, meta);
    }

    /**
     * End currently active file question by terminating common data and file specific data.
     *
     * @param questionId the question id
     * @param meta       the revision metadata used for terminating data
     */
    default void disableFileQuestion(long questionId, RevisionMetadata meta) {
        QuestionDto dto = getJdbiQuestion().findQuestionDtoById(questionId).orElse(null);
        FileQuestionDto questionDto = dto == null ? null : (FileQuestionDto) dto;
        if (questionDto == null || questionDto.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active file question with id " + questionId);
        }
        disableBaseQuestion(questionDto, meta);
    }

    /**
     * End currently active numeric question by terminating common data and numeric specific data.
     *
     * @param questionId the question id
     * @param meta       the revision metadata used for terminating data
     */
    default void disableNumericQuestion(long questionId, RevisionMetadata meta) {
        QuestionDto dto = getJdbiQuestion().findQuestionDtoById(questionId).orElse(null);
        NumericQuestionDto questionDto = dto == null ? null : (NumericQuestionDto) dto;
        if (questionDto == null || questionDto.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active numeric question with id " + questionId);
        }

        disableBaseQuestion(questionDto, meta);
        if (questionDto.getPlaceholderTemplateId() != null) {
            getTemplateDao().disableTemplate(questionDto.getPlaceholderTemplateId(), meta);
        }
    }

    /**
     * End currently active numeric question by terminating common data and numeric specific data.
     *
     * @param questionId the question id
     * @param meta       the revision metadata used for terminating data
     */
    default void disableDecimalQuestion(long questionId, RevisionMetadata meta) {
        QuestionDto dto = getJdbiQuestion().findQuestionDtoById(questionId).orElse(null);
        DecimalQuestionDto questionDto = dto == null ? null : (DecimalQuestionDto) dto;
        if (questionDto == null || questionDto.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active decimal question with id " + questionId);
        }

        disableBaseQuestion(questionDto, meta);
        if (questionDto.getPlaceholderTemplateId() != null) {
            getTemplateDao().disableTemplate(questionDto.getPlaceholderTemplateId(), meta);
        }
    }

    /**
     * End currently active numeric question by terminating common data and numeric specific data.
     *
     * @param questionId the question id
     * @param meta       the revision metadata used for terminating data
     */
    default void disableEquationQuestion(long questionId, RevisionMetadata meta) {
        EquationQuestionDto dto = getJdbiQuestion().findQuestionDtoById(questionId)
                .map(EquationQuestionDto.class::cast).orElse(null);
        if (dto == null || dto.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active equation question with id " + questionId);
        }

        disableBaseQuestion(dto, meta);
        if (dto.getPlaceholderTemplateId() != null) {
            getTemplateDao().disableTemplate(dto.getPlaceholderTemplateId(), meta);
        }
    }

    /**
     * End currently active picklist question by terminating common data and picklist specific data.
     *
     * @param questionId the question id
     * @param meta       the revision metadata used for terminating data
     */
    default void disablePicklistQuestion(long questionId, RevisionMetadata meta) {
        TemplateDao tmplDao = getTemplateDao();

        QuestionDto dto = getJdbiQuestion().findQuestionDtoById(questionId).orElse(null);
        PicklistQuestionDto picklistQuestion = dto == null ? null : (PicklistQuestionDto) dto;
        if (picklistQuestion == null || picklistQuestion.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active picklist question with id " + questionId);
        }
        disableBaseQuestion(picklistQuestion, meta);

        if (picklistQuestion.getLabelTemplateId() != null) {
            tmplDao.disableTemplate(picklistQuestion.getLabelTemplateId(), meta);
        }

        getPicklistQuestionDao().disableOptions(questionId, meta);
    }

    /**
     * End currently active matrix question by terminating common data and matrix specific data.
     *
     * @param questionId the question id
     * @param meta       the revision metadata used for terminating data
     */
    default void disableMatrixQuestion(long questionId, RevisionMetadata meta) {

        QuestionDto dto = getJdbiQuestion().findQuestionDtoById(questionId).orElse(null);
        MatrixQuestionDto matrixQuestion = dto == null ? null : (MatrixQuestionDto) dto;

        if (matrixQuestion == null || matrixQuestion.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active matrix question with id " + questionId);
        }

        disableBaseQuestion(matrixQuestion, meta);
        getMatrixQuestionDao().disableOptionsGroupsRowQuestions(questionId, meta);
    }

    /**
     * End currently active agreement question by terminating common data and agreement question specific data Since the
     * agreement question doesn't have any specific data so far, it boils down to the former
     */
    default void disableAgreementQuestion(long questionId, RevisionMetadata meta) {
        QuestionDto dto = getJdbiQuestion().findQuestionDtoById(questionId).orElse(null);
        AgreementQuestionDto questionDto = dto == null ? null : (AgreementQuestionDto) dto;
        if (questionDto == null || questionDto.getRevisionEnd() != null) {
            throw new NoSuchElementException("Cannot find active agreement question with id " + questionId);
        }
        disableBaseQuestion(questionDto, meta);
    }

    /**
     * Mark a question required with the given rule definition, if not already required.
     *
     * @param questionId the associated question
     * @param rule       the required rule definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void addRequiredRule(long questionId, RequiredRuleDef rule, long revisionId) {
        getJdbiQuestionValidation().getRequiredValidationIfActive(questionId).ifPresent(dto -> {
            throw new IllegalStateException("Question is already marked required with validation rule id " + dto
                    .getId());
        });
        getValidationDao().insert(questionId, rule, revisionId);
    }

    /**
     * Unmark the question as required by terminating the currently active required rule and its related data.
     *
     * @param questionId the associated question
     * @param meta       the revision metadata used for terminating data
     */
    default void disableRequiredRule(long questionId, RevisionMetadata meta) {
        RuleDto validation = getJdbiQuestionValidation().getRequiredValidationIfActive(questionId)
                .orElseThrow(() -> new NoSuchElementException("Question does not have a required validation rule"));
        getValidationDao().disableBaseRule(validation, meta);
    }

    default Map<Long, QuestionBlockDef> collectBlockDefs(Collection<FormBlockDto> blockDtos, long timestamp) {
        if (blockDtos == null || blockDtos.isEmpty()) {
            return new HashMap<>();
        }

        Set<Long> blockIds = new HashSet<>();
        for (var blockDto : blockDtos) {
            blockIds.add(blockDto.getId());
        }

        Map<Long, Long> blockIdToQuestionId = getJdbiBlockQuestion()
                .findQuestionIdsByBlockIdsAndTimestamp(blockIds, timestamp);
        Map<Long, QuestionDef> questionDefs = collectQuestionDefs(blockIdToQuestionId.values(), timestamp);

        Map<Long, QuestionBlockDef> blockDefs = new HashMap<>();
        for (var blockDto : blockDtos) {
            long questionId = blockIdToQuestionId.get(blockDto.getId());
            QuestionDef questionDef = questionDefs.get(questionId);

            var blockDef = new QuestionBlockDef(questionDef);
            blockDef.setBlockId(blockDto.getId());
            blockDef.setBlockGuid(blockDto.getGuid());
            
            var shownExpression = blockDto.getShownExpression();
            if (shownExpression != null) {
                blockDef.setShownExprId(shownExpression.getId());
                blockDef.setShownExpr(shownExpression.getText());
            }

            var enabledExpression = blockDto.getEnabledExpression();
            if (enabledExpression != null) {
                blockDef.setEnabledExprId(enabledExpression.getId());
                blockDef.setEnabledExpr(enabledExpression.getText());
            }

            blockDefs.put(blockDto.getId(), blockDef);
        }

        return blockDefs;
    }

    default Map<Long, QuestionBlockDef> collectTabularBlockDefs(Collection<BlockTabularQuestionDto> blockDtos, long timestamp) {
        if (blockDtos == null || blockDtos.isEmpty()) {
            return new HashMap<>();
        }

        final var blockIds = StreamEx.of(blockDtos).map(BlockTabularQuestionDto::getQuestionBlockId).toSet();

        Map<Long, Long> blockIdToQuestionId = getJdbiBlockQuestion()
                .findQuestionIdsByBlockIdsAndTimestamp(blockIds, timestamp);
        Map<Long, QuestionDef> questionDefs = collectQuestionDefs(blockIdToQuestionId.values(), timestamp);

        Map<Long, QuestionBlockDef> blockDefs = new HashMap<>();
        for (var blockDto : blockDtos) {
            long questionId = blockIdToQuestionId.get(blockDto.getQuestionBlockId());
            QuestionDef questionDef = questionDefs.get(questionId);

            var blockDef = new QuestionBlockDef(questionDef);
            blockDef.setBlockId(blockDto.getQuestionBlockId());
            blockDef.setBlockGuid(blockDto.getBlockGuid());
            blockDef.setShownExpr(blockDto.getShownExpr());
            blockDef.setEnabledExpr(blockDto.getEnabledExpr());
            blockDef.setColumnSpan(blockDto.getColumnSpan());
            blockDefs.put(blockDto.getQuestionBlockId(), blockDef);
        }

        return blockDefs;
    }

    default Map<Long, QuestionDef> collectQuestionDefs(Collection<Long> questionIds, long timestamp) {
        if (questionIds == null || questionIds.isEmpty()) {
            return new HashMap<>();
        }

        List<QuestionDto> questionDtos;
        try (var stream = getJdbiQuestion().findQuestionDtosByIds(questionIds)) {
            questionDtos = stream.collect(toList());
        }
        Map<Long, List<RuleDef>> questionIdToRuleDefs = getValidationDao().collectRuleDefs(questionIds, timestamp);

        Set<Long> templateIds = new HashSet<>();
        for (var questionDto : questionDtos) {
            templateIds.addAll(questionDto.getTemplateIds());
        }
        Map<Long, Template> templates = getTemplateDao().collectTemplatesByIdsAndTimestamp(templateIds, timestamp);

        Map<Long, QuestionDef> questionDefs = new HashMap<>();
        List<PicklistQuestionDto> picklistDtos = new ArrayList<>();
        List<MatrixQuestionDto> matrixDtos = new ArrayList<>();
        List<CompositeQuestionDto> compositeDtos = new ArrayList<>();

        for (var questionDto : questionDtos) {
            long questionId = questionDto.getId();
            List<RuleDef> ruleDefs = questionIdToRuleDefs.getOrDefault(questionId, new ArrayList<>());
            QuestionDef questionDef;
            switch (questionDto.getType()) {
                case AGREEMENT:
                    questionDef = buildAgreementQuestionDef((AgreementQuestionDto) questionDto, ruleDefs, templates);
                    questionDefs.put(questionId, questionDef);
                    break;
                case BOOLEAN:
                    questionDef = buildBoolQuestionDef((BooleanQuestionDto) questionDto, ruleDefs, templates);
                    questionDefs.put(questionId, questionDef);
                    break;
                case DATE:
                    questionDef = buildDateQuestionDef((DateQuestionDto) questionDto, ruleDefs, templates);
                    questionDefs.put(questionId, questionDef);
                    break;
                case FILE:
                    questionDef = buildFileQuestionDef((FileQuestionDto) questionDto, ruleDefs, templates);
                    questionDefs.put(questionId, questionDef);
                    break;
                case NUMERIC:
                    questionDef = buildNumericQuestionDef((NumericQuestionDto) questionDto, ruleDefs, templates);
                    questionDefs.put(questionId, questionDef);
                    break;
                case DECIMAL:
                    questionDef = buildDecimalQuestionDef((DecimalQuestionDto) questionDto, ruleDefs, templates);
                    questionDefs.put(questionId, questionDef);
                    break;
                case EQUATION:
                    questionDef = buildEquationQuestionDef((EquationQuestionDto) questionDto, ruleDefs, templates);
                    questionDefs.put(questionId, questionDef);
                    break;
                case PICKLIST:
                    picklistDtos.add((PicklistQuestionDto) questionDto);
                    break;
                case MATRIX:
                    matrixDtos.add((MatrixQuestionDto) questionDto);
                    break;
                case TEXT:
                    questionDef = buildTextQuestionDef((TextQuestionDto) questionDto, ruleDefs, templates);
                    questionDefs.put(questionId, questionDef);
                    break;
                case ACTIVITY_INSTANCE_SELECT:
                    questionDef = buildActivityInstanceSelectQuestionDef((ActivityInstanceSelectQuestionDto) questionDto,
                            ruleDefs, templates);
                    questionDefs.put(questionId, questionDef);
                    break;
                case COMPOSITE:
                    compositeDtos.add((CompositeQuestionDto) questionDto);
                    break;
                default:
                    throw new DaoException("Unhandled question type " + questionDto.getType());
            }
        }

        questionDefs.putAll(collectPicklistQuestionDefs(picklistDtos, questionIdToRuleDefs, templates, timestamp));
        questionDefs.putAll(collectMatrixQuestionDefs(matrixDtos, questionIdToRuleDefs, templates, timestamp));
        questionDefs.putAll(collectCompositeQuestionDefs(compositeDtos, questionIdToRuleDefs, templates, timestamp));

        return questionDefs;
    }

    private Map<Long, PicklistQuestionDef> collectPicklistQuestionDefs(Collection<PicklistQuestionDto> picklistDtos,
                                                                       Map<Long, List<RuleDef>> questionIdToRuleDefs,
                                                                       Map<Long, Template> templates,
                                                                       long timestamp) {
        Set<Long> questionIds = new HashSet<>();
        for (var picklistDto : picklistDtos) {
            questionIds.add(picklistDto.getId());
        }

        Map<Long, PicklistQuestionDao.GroupAndOptionDtos> questionIdToContainer = getPicklistQuestionDao()
                .findOrderedGroupAndOptionDtos(questionIds, timestamp);

        Set<Long> templateIds = new HashSet<>();
        for (var picklistDto : picklistDtos) {
            templateIds.addAll(picklistDto.getTemplateIds());
        }
        for (var container : questionIdToContainer.values()) {
            templateIds.addAll(container.getTemplateIds());
        }
        Map<Long, Template> picklistTemplates = getTemplateDao().collectTemplatesByIdsAndTimestamp(templateIds, timestamp);
        templates.putAll(picklistTemplates);

        Map<Long, PicklistQuestionDef> picklistDefs = new HashMap<>();
        for (var picklistDto : picklistDtos) {
            long questionId = picklistDto.getId();
            List<RuleDef> ruleDefs = questionIdToRuleDefs.getOrDefault(questionId, new ArrayList<>());
            var container = questionIdToContainer.get(questionId);
            var picklistDef = buildPicklistQuestionDef(picklistDto, container, ruleDefs, templates);
            picklistDefs.put(questionId, picklistDef);
        }

        return picklistDefs;
    }

    private Map<Long, MatrixQuestionDef> collectMatrixQuestionDefs(Collection<MatrixQuestionDto> matrixDtos,
                                                                   Map<Long, List<RuleDef>> questionIdToRuleDefs,
                                                                   Map<Long, Template> templates,
                                                                   long timestamp) {
        Set<Long> questionIds = new HashSet<>();
        for (var matrixDto : matrixDtos) {
            questionIds.add(matrixDto.getId());
        }

        Map<Long, MatrixQuestionDao.GroupOptionRowDtos> questionIdToContainer = getMatrixQuestionDao()
                .findOrderedGroupOptionRowDtos(questionIds, timestamp);

        Set<Long> templateIds = new HashSet<>();

        for (var matrixDto : matrixDtos) {
            templateIds.addAll(matrixDto.getTemplateIds());
        }

        for (var container : questionIdToContainer.values()) {
            templateIds.addAll(container.getTemplateIds());
        }

        Map<Long, Template> matrixTemplates = getTemplateDao().collectTemplatesByIdsAndTimestamp(templateIds, timestamp);
        templates.putAll(matrixTemplates);

        Map<Long, MatrixQuestionDef> matrixDefs = new HashMap<>();
        for (var matrixDto : matrixDtos) {
            long questionId = matrixDto.getId();
            List<RuleDef> ruleDefs = questionIdToRuleDefs.getOrDefault(questionId, new ArrayList<>());
            var container = questionIdToContainer.get(questionId);
            var matrixDef = buildMatrixQuestionDef(matrixDto, container, ruleDefs, templates);
            matrixDefs.put(questionId, matrixDef);
        }

        return matrixDefs;
    }

    private Map<Long, CompositeQuestionDef> collectCompositeQuestionDefs(Collection<CompositeQuestionDto> compositeDtos,
                                                                         Map<Long, List<RuleDef>> questionIdToRuleDefs,
                                                                         Map<Long, Template> templates,
                                                                         long timestamp) {
        Set<Long> questionIds = new HashSet<>();
        for (var compositeDto : compositeDtos) {
            questionIds.add(compositeDto.getId());
        }

        Map<Long, List<Long>> parentIdToChildIds = getJdbiQuestion()
                .collectOrderedCompositeChildIdsByParentIdsAndTimestamp(questionIds, timestamp);

        Set<Long> allChildIds = new HashSet<>();
        for (var childIds : parentIdToChildIds.values()) {
            allChildIds.addAll(childIds);
        }
        Map<Long, QuestionDef> allChildDefs = collectQuestionDefs(allChildIds, timestamp);

        Map<Long, CompositeQuestionDef> compositeDefs = new HashMap<>();
        for (var compositeDto : compositeDtos) {
            long questionId = compositeDto.getId();
            List<RuleDef> ruleDefs = questionIdToRuleDefs.getOrDefault(questionId, new ArrayList<>());

            List<QuestionDef> childDefs = new ArrayList<>();
            List<Long> childIds = parentIdToChildIds.getOrDefault(questionId, new ArrayList<>());
            for (var childId : childIds) {
                childDefs.add(allChildDefs.get(childId));
            }

            var compositeDef = buildCompositeQuestionDef(compositeDto, childDefs, ruleDefs, templates);
            compositeDefs.put(questionId, compositeDef);
        }

        return compositeDefs;
    }

    private void configureBaseQuestionDef(QuestionDef.AbstractQuestionBuilder builder,
                                          QuestionDto questionDto,
                                          List<RuleDef> ruleDefs,
                                          Map<Long, Template> templates) {
        Template tooltipTemplate = templates.getOrDefault(questionDto.getTooltipTemplateId(), null);
        Template headerTemplate = templates.getOrDefault(questionDto.getAdditionalInfoHeaderTemplateId(), null);
        Template footerTemplate = templates.getOrDefault(questionDto.getAdditionalInfoFooterTemplateId(), null);
        builder.addValidations(ruleDefs)
                .setQuestionId(questionDto.getId())
                .setRestricted(questionDto.isRestricted())
                .setDeprecated(questionDto.isDeprecated())
                .setWriteOnce(questionDto.isWriteOnce())
                .setHideNumber(questionDto.shouldHideNumber())
                .setAdditionalInfoHeader(headerTemplate)
                .setAdditionalInfoFooter(footerTemplate)
                .setTooltip(tooltipTemplate);
    }

    private void configureBaseQuestionDef(QuestionDef.QuestionDefBuilder builder,
                                          QuestionDto questionDto,
                                          List<RuleDef> ruleDefs,
                                          Map<Long, Template> templates) {
        builder.validations(ruleDefs)
                .questionId(questionDto.getId())
                .isRestricted(questionDto.isRestricted())
                .isDeprecated(questionDto.isDeprecated())
                .writeOnce(questionDto.isWriteOnce())
                .hideNumber(questionDto.shouldHideNumber())
                .additionalInfoHeaderTemplate(templates.getOrDefault(questionDto.getAdditionalInfoHeaderTemplateId(), null))
                .additionalInfoFooterTemplate(templates.getOrDefault(questionDto.getAdditionalInfoFooterTemplateId(), null))
                .tooltipTemplate(templates.getOrDefault(questionDto.getTooltipTemplateId(), null));
    }

    private AgreementQuestionDef buildAgreementQuestionDef(AgreementQuestionDto dto,
                                                           List<RuleDef> ruleDefs,
                                                           Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());
        var builder = AgreementQuestionDef.builder(dto.getStableId(), prompt);
        configureBaseQuestionDef(builder, dto, ruleDefs, templates);
        return builder.build();
    }

    private BoolQuestionDef buildBoolQuestionDef(BooleanQuestionDto dto,
                                                 List<RuleDef> ruleDefs,
                                                 Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());
        Template trueTemplate = templates.get(dto.getTrueTemplateId());
        Template falseTemplate = templates.get(dto.getFalseTemplateId());

        var builder = BoolQuestionDef.builder(dto.getStableId(), prompt, trueTemplate, falseTemplate);
        builder.setRenderMode(dto.getRenderMode());
        
        configureBaseQuestionDef(builder, dto, ruleDefs, templates);
        return builder.build();
    }

    private DateQuestionDef buildDateQuestionDef(DateQuestionDto dto,
                                                 List<RuleDef> ruleDefs,
                                                 Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());
        Template placeholderTemplate = templates.getOrDefault(dto.getPlaceholderTemplateId(), null);

        DatePicklistDef picklistDef = null;
        if (dto.getRenderMode() == DateRenderMode.PICKLIST) {
            picklistDef = dto.getPicklistDef();
        }

        var builder = DateQuestionDef
                .builder(dto.getRenderMode(), dto.getStableId(), prompt)
                .setDisplayCalendar(dto.shouldDisplayCalendar())
                .setPlaceholderTemplate(placeholderTemplate)
                .setPicklistDef(picklistDef)
                .addFields(dto.getFields());
        configureBaseQuestionDef(builder, dto, ruleDefs, templates);

        return builder.build();
    }

    private FileQuestionDef buildFileQuestionDef(FileQuestionDto dto,
                                                 List<RuleDef> ruleDefs,
                                                 Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());
        var builder = FileQuestionDef.builder(dto.getStableId(), prompt)
                .setMaxFileSize(dto.getMaxFileSize())
                .setMimeTypes(dto.getMimeTypes());
        configureBaseQuestionDef(builder, dto, ruleDefs, templates);
        return builder.build();
    }

    private NumericQuestionDef buildNumericQuestionDef(NumericQuestionDto dto,
                                                       List<RuleDef> ruleDefs,
                                                       Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());
        Template placeholderTemplate = templates.getOrDefault(dto.getPlaceholderTemplateId(), null);
        var builder = NumericQuestionDef
                .builder(dto.getStableId(), prompt)
                .setPlaceholderTemplate(placeholderTemplate);
        configureBaseQuestionDef(builder, dto, ruleDefs, templates);
        return builder.build();
    }

    private DecimalQuestionDef buildDecimalQuestionDef(DecimalQuestionDto dto,
                                                       List<RuleDef> ruleDefs,
                                                       Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());
        Template placeholderTemplate = templates.getOrDefault(dto.getPlaceholderTemplateId(), null);
        var builder = DecimalQuestionDef
                .builder(dto.getStableId(), prompt)
                .setPlaceholderTemplate(placeholderTemplate)
                .setScale(dto.getScale());
        configureBaseQuestionDef(builder, dto, ruleDefs, templates);
        return builder.build();
    }

    private EquationQuestionDef buildEquationQuestionDef(EquationQuestionDto dto,
                                                         List<RuleDef> ruleDefs,
                                                         Map<Long, Template> templates) {
        return EquationQuestionDef
                .builder()
                .questionType(dto.getType())
                .stableId(dto.getStableId())
                .promptTemplate(templates.get(dto.getPromptTemplateId()))
                .placeholderTemplate(templates.getOrDefault(dto.getPlaceholderTemplateId(), null))
                .maximumDecimalPlaces(dto.getMaximumDecimalPlaces())
                .expression(dto.getExpression())
                .validations(ruleDefs)
                .questionId(dto.getId())
                .isRestricted(dto.isRestricted())
                .isDeprecated(dto.isDeprecated())
                .writeOnce(dto.isWriteOnce())
                .hideNumber(dto.shouldHideNumber())
                .additionalInfoHeaderTemplate(templates.getOrDefault(dto.getAdditionalInfoHeaderTemplateId(), null))
                .additionalInfoFooterTemplate(templates.getOrDefault(dto.getAdditionalInfoFooterTemplateId(), null))
                .tooltipTemplate(templates.getOrDefault(dto.getTooltipTemplateId(), null))
                .build();
    }

    private TextQuestionDef buildTextQuestionDef(TextQuestionDto dto,
                                                 List<RuleDef> ruleDefs,
                                                 Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());
        Template placeholderTemplate = templates.getOrDefault(dto.getPlaceholderTemplateId(), null);
        Template confirmPlaceholderTemplate = templates.getOrDefault(dto.getConfirmPlaceholderTemplateId(), null);
        Template confirmPromptTemplate = templates.getOrDefault(dto.getConfirmPromptTemplateId(), null);
        Template mismatchMessageTemplate = templates.getOrDefault(dto.getMismatchMessageTemplateId(), null);

        List<String> suggestions = new ArrayList<>();
        if (dto.getSuggestionType() == SuggestionType.INCLUDED) {
            suggestions = getJdbiQuestion().findTextQuestionSuggestions(dto.getId());
        }

        var builder = TextQuestionDef
                .builder(dto.getInputType(), dto.getStableId(), prompt)
                .setSuggestionType(dto.getSuggestionType())
                .setPlaceholderTemplate(placeholderTemplate)
                .setConfirmPlaceholderTemplate(confirmPlaceholderTemplate)
                .setConfirmEntry(dto.isConfirmEntry())
                .setConfirmPromptTemplate(confirmPromptTemplate)
                .setMismatchMessage(mismatchMessageTemplate)
                .addSuggestions(suggestions);
        configureBaseQuestionDef(builder, dto, ruleDefs, templates);

        return builder.build();
    }

    private ActivityInstanceSelectQuestionDef buildActivityInstanceSelectQuestionDef(ActivityInstanceSelectQuestionDto dto,
                                                                                     List<RuleDef> ruleDefs,
                                                                                     Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());

        List<String> activityCodes = getJdbiQuestion().getActivityCodesByActivityInstanceSelectQuestionId(dto.getId());

        var builder = ActivityInstanceSelectQuestionDef
                .builder(dto.getStableId(), prompt)
                .setActivityCodes(activityCodes);

        configureBaseQuestionDef(builder, dto, ruleDefs, templates);

        return builder.build();
    }

    private PicklistQuestionDef buildPicklistQuestionDef(PicklistQuestionDto dto,
                                                         PicklistQuestionDao.GroupAndOptionDtos container,
                                                         List<RuleDef> ruleDefs,
                                                         Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());
        Template label = null;
        if (dto.getRenderMode() == PicklistRenderMode.DROPDOWN) {
            label = templates.get(dto.getLabelTemplateId());
        }

        List<PicklistGroupDef> groups = new ArrayList<>();
        for (PicklistGroupDto groupDto : container.getGroups()) {
            Template nameTemplate = templates.get(groupDto.getNameTemplateId());
            List<PicklistOptionDef> options = container.getGroupIdToOptions().get(groupDto.getId())
                    .stream().map(optionDto -> {
                        Template optionLabel = templates.get(optionDto.getOptionLabelTemplateId());
                        Template detailLabel = !optionDto.isAllowDetails() ? null
                                : templates.get(optionDto.getDetailLabelTemplateId());
                        Template tooltipTemplate = templates.getOrDefault(optionDto.getTooltipTemplateId(), null);
                        return new PicklistOptionDef(optionDto.getId(), optionDto.getStableId(),
                                optionLabel, tooltipTemplate, detailLabel, optionDto.isExclusive(), optionDto.isDefault());
                    })
                    .collect(Collectors.toList());
            groups.add(new PicklistGroupDef(groupDto.getId(), groupDto.getStableId(), nameTemplate, options));
        }

        List<PicklistOptionDef> ungroupedOptions = container.getUngroupedOptions()
                .stream().map(optionDto -> {
                    Template optionLabel = templates.get(optionDto.getOptionLabelTemplateId());
                    Template detailLabel = !optionDto.isAllowDetails() ? null
                            : templates.get(optionDto.getDetailLabelTemplateId());
                    Template tooltipTemplate = templates.getOrDefault(optionDto.getTooltipTemplateId(), null);
                    Template nestedOptionsTemplate = templates.getOrDefault(optionDto.getNestedOptionsTemplateId(), null);

                    PicklistOptionDef optionDef = null;
                    if (CollectionUtils.isEmpty(optionDto.getNestedOptions())) {
                        optionDef = new PicklistOptionDef(optionDto.getId(), optionDto.getStableId(),
                                optionLabel, tooltipTemplate, detailLabel, optionDto.isExclusive(), optionDto.isDefault());
                    } else {
                        List<PicklistOptionDef> nestedOptions = new ArrayList<>();
                        for (PicklistOptionDto nestedOptionDto : optionDto.getNestedOptions()) {
                            Template nestedOptionLabel = templates.get(nestedOptionDto.getOptionLabelTemplateId());
                            Template nestedDetailLabel = !nestedOptionDto.isAllowDetails() ? null
                                    : templates.get(nestedOptionDto.getDetailLabelTemplateId());
                            Template nestedTooltipTemplate = templates.getOrDefault(nestedOptionDto.getTooltipTemplateId(), null);
                            nestedOptions.add(new PicklistOptionDef(nestedOptionDto.getId(), nestedOptionDto.getStableId(),
                                    nestedOptionLabel, nestedTooltipTemplate, nestedDetailLabel,
                                    nestedOptionDto.isExclusive(), nestedOptionDto.isDefault()));
                        }
                        optionDef = new PicklistOptionDef(optionDto.getId(), optionDto.getStableId(),
                                optionLabel, tooltipTemplate, detailLabel, optionDto.isExclusive(),
                                optionDto.isDefault(), nestedOptionsTemplate, nestedOptions);
                    }
                    return optionDef;
                })
                .collect(Collectors.toList());

        var builder = PicklistQuestionDef
                .builder(dto.getSelectMode(), dto.getRenderMode(), dto.getStableId(), prompt)
                .setLabel(label)
                .addGroups(groups)
                .addOptions(ungroupedOptions);
        configureBaseQuestionDef(builder, dto, ruleDefs, templates);

        return builder.build();
    }

    private MatrixQuestionDef buildMatrixQuestionDef(MatrixQuestionDto dto,
                                                     MatrixQuestionDao.GroupOptionRowDtos container,
                                                     List<RuleDef> ruleDefs,
                                                     Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());
        Template modal = templates.get(dto.getModalTemplateId());
        Template modalTitle = templates.get(dto.getModalTitleTemplateId());

        List<MatrixGroupDef> groups = new ArrayList<>();
        for (MatrixGroupDto groupDto : container.getGroups()) {
            if (groupDto.getStableId() == null) {
                continue;
            }
            Template nameTemplate = null;
            if (groupDto.getNameTemplateId() != null) {
                nameTemplate = templates.get(groupDto.getNameTemplateId());
            }
            groups.add(new MatrixGroupDef(groupDto.getId(), groupDto.getStableId(), nameTemplate));
        }

        List<MatrixOptionDef> options = container.getOptions().stream().map(optionDto -> {
            Template optionLabel = templates.get(optionDto.getOptionLabelTemplateId());
            Template tooltipTemplate = templates.getOrDefault(optionDto.getTooltipTemplateId(), null);
            return new MatrixOptionDef(optionDto.getId(), optionDto.getStableId(), optionLabel, tooltipTemplate,
                    getJdbiMatrixGroup().findGroupCodeById(optionDto.getGroupId()), optionDto.isExclusive());
        }).collect(Collectors.toList());

        List<MatrixRowDef> questions = container.getRows().stream().map(questionDto -> {
            Template questionLabel = templates.get(questionDto.getQuestionLabelTemplateId());
            Template tooltipTemplate = templates.getOrDefault(questionDto.getTooltipTemplateId(), null);
            return new MatrixRowDef(questionDto.getId(), questionDto.getStableId(), questionLabel, tooltipTemplate);
        }).collect(Collectors.toList());

        var builder = MatrixQuestionDef
                .builder(dto.getSelectMode(), dto.getStableId(), prompt)
                .setSelectMode(dto.getSelectMode())
                .setRenderModal(dto.isRenderModal())
                .setModalTemplate(modal)
                .setModalTitleTemplate(modalTitle)
                .addGroups(groups)
                .addOptions(options)
                .addRows(questions);
        configureBaseQuestionDef(builder, dto, ruleDefs, templates);

        return builder.build();
    }

    private CompositeQuestionDef buildCompositeQuestionDef(CompositeQuestionDto dto,
                                                           List<QuestionDef> childQuestionDefs,
                                                           List<RuleDef> ruleDefs,
                                                           Map<Long, Template> templates) {
        Template prompt = templates.get(dto.getPromptTemplateId());
        Template buttonTmpl = templates.getOrDefault(dto.getAddButtonTemplateId(), null);
        Template addItemTmpl = templates.getOrDefault(dto.getAdditionalItemTemplateId(), null);

        var builder = CompositeQuestionDef.builder()
                .setStableId(dto.getStableId())
                .setPrompt(prompt)
                .setAllowMultiple(dto.isAllowMultiple())
                .setUnwrapOnExport(dto.isUnwrapOnExport())
                .setAddButtonTemplate(buttonTmpl)
                .setAdditionalItemTemplate(addItemTmpl)
                .setChildOrientation(dto.getChildOrientation())
                .setTabularSeparator(dto.getTabularSeparator())
                .addChildrenQuestions(childQuestionDefs);
        configureBaseQuestionDef(builder, dto, ruleDefs, templates);

        return builder.build();
    }
}
