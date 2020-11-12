package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.AgreementQuestionDto;
import org.broadinstitute.ddp.db.dto.BooleanQuestionDto;
import org.broadinstitute.ddp.db.dto.CompositeQuestionDto;
import org.broadinstitute.ddp.db.dto.DateQuestionDto;
import org.broadinstitute.ddp.db.dto.NumericQuestionDto;
import org.broadinstitute.ddp.db.dto.PicklistGroupDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.PicklistQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.TextQuestionDto;
import org.broadinstitute.ddp.db.dto.TypedQuestionId;
import org.broadinstitute.ddp.db.dto.validation.ValidationDto;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.AgreementQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DatePicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
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
    JdbiPicklistOption getJdbiPicklistOption();

    @CreateSqlObject
    JdbiCompositeQuestion getJdbiCompositeQuestion();

    @CreateSqlObject
    JdbiNumericQuestion getJdbiNumericQuestion();

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
    JdbiAgreementQuestion getJdbiAgreementQuestion();

    @CreateSqlObject
    AnswerDao getAnswerDao();

    /**
     * Get the non-deprecated question for a given block.
     *
     * @param blockId              the block id
     * @param activityInstanceGuid the form instance guid
     * @return single question, if it's not deprecated
     */
    default Optional<Question> getQuestionByBlockId(long blockId,
                                                    String activityInstanceGuid,
                                                    long langCodeId) {
        return getQuestionByBlockId(blockId, activityInstanceGuid, false, langCodeId);
    }


    /**
     * Get the question for a given block. Toggle if okay with deprecated questions.
     *
     * @param blockId              the block id
     * @param activityInstanceGuid the form instance guid
     * @param includeDeprecated    flag indicating whether or nto to include deprecated questions
     * @return single question, if it's not deprecated
     */
    default Optional<Question> getQuestionByBlockId(long blockId,
                                                    String activityInstanceGuid,
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
            return Optional.of(getQuestionByActivityInstanceAndDto(dto, activityInstanceGuid, langCodeId));
        }
    }

    /**
     * Get the non-deprecated control question for a conditional block.
     *
     * @param blockId              the block id
     * @param activityInstanceGuid the form instance guid
     * @return control question, if it's not deprecated
     */
    default Optional<Question> getControlQuestionByBlockId(long blockId,
                                                           String activityInstanceGuid,
                                                           long langCodeId) {
        return getControlQuestionByBlockId(blockId, activityInstanceGuid, false, langCodeId);
    }

    /**
     * Get the control question for a conditional block. This allows fetching deprecated control questions. Prefer the
     * other method that excludes them.
     *
     * @param blockId              the block id
     * @param activityInstanceGuid the form instance guid
     * @return control question
     */
    default Optional<Question> getControlQuestionByBlockId(long blockId,
                                                           String activityInstanceGuid,
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
            return Optional.of(getQuestionByActivityInstanceAndDto(questionDto, activityInstanceGuid, true, langCodeId));
        }
    }

    /**
     * Get a question for a given id. Gets answers as well if it has any.
     *
     * @param questionId           the block id
     * @param activityInstanceGuid the form instance guid
     * @return single question
     */
    default Question getQuestionByIdAndActivityInstanceGuid(long questionId, String activityInstanceGuid, long langCodeId) {
        return getQuestionByIdAndActivityInstanceGuid(questionId, activityInstanceGuid, true, langCodeId);
    }

    /**
     * Get a question for a given id. Toggle get answers as well if it has any.
     *
     * @param questionId           the block id
     * @param activityInstanceGuid the form instance guid
     * @return single question
     */
    default Question getQuestionByIdAndActivityInstanceGuid(
            long questionId,
            String activityInstanceGuid,
            boolean retrieveAnswers,
            long langCodeId
    ) {
        return getJdbiQuestion().findQuestionDtoById(questionId)
                .filter(dto -> dto.getRevisionEnd() == null)
                .map(dto -> getQuestionByActivityInstanceAndDto(dto, activityInstanceGuid, retrieveAnswers, langCodeId))
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
                .findLatestInstanceGuidFromUserGuidAndQuestionId(userGuid, dto.getId())
                .map(instanceGuid -> getQuestionByActivityInstanceAndDto(dto, instanceGuid, retrieveAnswers, langCodeId))
                .orElseThrow(null);
    }

    /**
     * Get question by activity instance and dto. Gets answers as well if it has any.
     *
     * @param dto                  question dto
     * @param activityInstanceGuid the form instance guid
     * @return single question
     */
    default Question getQuestionByActivityInstanceAndDto(QuestionDto dto, String activityInstanceGuid, long langCodeId) {
        return getQuestionByActivityInstanceAndDto(dto, activityInstanceGuid, true, langCodeId);
    }

    /**
     * Get question by activity instance and dto. Toggles getting answers as well if it has any.
     *
     * @param dto                  question dto
     * @param activityInstanceGuid the form instance guid
     * @param retrieveAnswers      flag indicating whether to get answers for question
     * @return single question
     */
    default Question getQuestionByActivityInstanceAndDto(QuestionDto dto,
                                                         String activityInstanceGuid,
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

        List<Rule> untypedRules = getValidationDao().getValidationRules(dto, langCodeId);

        Question question;

        switch (dto.getType()) {
            case BOOLEAN:
                question = getBooleanQuestion((BooleanQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case PICKLIST:
                question = getPicklistQuestion((PicklistQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case TEXT:
                question = getTextQuestion((TextQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case DATE:
                question = getDateQuestion((DateQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case NUMERIC:
                question = getNumericQuestion((NumericQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case AGREEMENT:
                question = getAgreementQuestion((AgreementQuestionDto) dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case COMPOSITE:
                question = getCompositeQuestion((CompositeQuestionDto) dto,
                        activityInstanceGuid, answerIds, untypedRules, retrieveAnswers, langCodeId);
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
                dto.getFalseTemplateId());
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

        long timestamp = getHandle().attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(activityInstanceGuid)
                .map(ActivityInstanceDto::getCreatedAtMillis)
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find activity instance with guid=%s while getting picklist question with id=%d and stableId=%s",
                        activityInstanceGuid, dto.getId(), dto.getStableId())));

        PicklistQuestionDao.GroupAndOptionDtos container = getPicklistQuestionDao()
                .findOrderedGroupAndOptionDtos(dto.getId(), timestamp);

        List<PicklistGroup> groups = new ArrayList<>();
        List<PicklistOption> allOptions = new ArrayList<>();

        PicklistOption option = null;
        // Put the ungrouped options in the list first.
        for (PicklistOptionDto optionDto : container.getUngroupedOptions()) {
            if (CollectionUtils.isEmpty(optionDto.getNestedOptions())) {
                option = new PicklistOption(optionDto.getStableId(),
                        optionDto.getOptionLabelTemplateId(), optionDto.getTooltipTemplateId(), optionDto.getDetailLabelTemplateId(),
                        optionDto.getAllowDetails(), optionDto.isExclusive());
            } else {
                //add nested options
                List<PicklistOption> nestedOptions = new ArrayList<>();
                optionDto.getNestedOptions().stream().forEach(nestedOptionDto -> {
                    nestedOptions.add(new PicklistOption(nestedOptionDto.getStableId(),
                            nestedOptionDto.getOptionLabelTemplateId(), nestedOptionDto.getTooltipTemplateId(),
                            nestedOptionDto.getDetailLabelTemplateId(), nestedOptionDto.getAllowDetails(),
                            nestedOptionDto.isExclusive()));
                });

                option = new PicklistOption(optionDto.getStableId(),
                        optionDto.getOptionLabelTemplateId(), optionDto.getTooltipTemplateId(), optionDto.getDetailLabelTemplateId(),
                        optionDto.getAllowDetails(), optionDto.isExclusive(), optionDto.getNestedOptionsTemplateId(), nestedOptions);
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
                        optionDto.getAllowDetails(), optionDto.isExclusive()));
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
                rules,
                dto.getNumericType());
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
     * @param dto                  the question dto
     * @param activityInstanceGuid the activity instance guid
     * @param answerIds            list of base answer ids to question (may be empty)
     * @param untypedRules         list of untyped validations for question (may be empty)
     * @return composite question object
     */
    default CompositeQuestion getCompositeQuestion(CompositeQuestionDto dto,
                                                   String activityInstanceGuid,
                                                   List<Long> answerIds,
                                                   List<Rule> untypedRules,
                                                   boolean retrieveAnswers,
                                                   long langCodeId) {
        JdbiQuestion jdbiQuestion = getJdbiQuestion();
        List<Long> childIds = jdbiQuestion.findCompositeChildIdsByParentId(dto.getId());
        List<Question> childQuestions;
        try (var stream = jdbiQuestion.findQuestionDtosByIds(childIds)) {
            childQuestions = stream
                    .map(childQuestionDto ->
                            getQuestionByActivityInstanceAndDto(childQuestionDto, activityInstanceGuid, retrieveAnswers, langCodeId))
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
                childQuestions, dto.getChildOrientation(), compositeAnswers);
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
            case DATE:
                insertQuestion(activityId, (DateQuestionDef) question, revisionId);
                break;
            case NUMERIC:
                insertQuestion(activityId, (NumericQuestionDef) question, revisionId);
                break;
            case PICKLIST:
                insertQuestion(activityId, (PicklistQuestionDef) question, revisionId);
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
            case DATE:
                disableDateQuestion(qid.getId(), meta);
                break;
            case NUMERIC:
                disableNumericQuestion(qid.getId(), meta);
                break;
            case PICKLIST:
                disablePicklistQuestion(qid.getId(), meta);
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
                question.isDeprecated());
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
                boolQuestion.getTrueTemplate().getTemplateId(), boolQuestion.getFalseTemplate().getTemplateId());
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
        Long placeholderTemplateId = null;
        if (textQuestion.getPlaceholderTemplate() != null) {
            placeholderTemplateId = templateDao.insertTemplate(textQuestion.getPlaceholderTemplate(), revisionId);
        }

        Long confirmEntryTemplateId = null;
        if (textQuestion.getConfirmPromptTemplate() != null) {
            confirmEntryTemplateId = templateDao.insertTemplate(textQuestion.getConfirmPromptTemplate(), revisionId);
        }

        Long mismatchMessageTemplateId = null;
        if (textQuestion.getMismatchMessageTemplate() != null) {
            mismatchMessageTemplateId = templateDao.insertTemplate(textQuestion.getMismatchMessageTemplate(), revisionId);
        }

        int numInserted = getJdbiTextQuestion().insert(textQuestion.getQuestionId(),
                textQuestion.getInputType(),
                textQuestion.getSuggestionType(),
                placeholderTemplateId,
                textQuestion.isConfirmEntry(),
                confirmEntryTemplateId,
                mismatchMessageTemplateId);
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for text question " + textQuestion.getStableId());
        }

        if (CollectionUtils.isNotEmpty(textQuestion.getSuggestions())) {
            int displayOrder = 0;
            for (String suggestion : textQuestion.getSuggestions()) {
                displayOrder += DISPLAY_ORDER_GAP;
                numInserted = getJdbiTextQuestionSuggestion().insert(textQuestion.getQuestionId(),
                        suggestion, displayOrder);
                if (numInserted != 1) {
                    throw new DaoException("Inserted " + numInserted + " for text question: " + textQuestion.getStableId()
                            + "  suggestion: " + suggestion);
                }
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

        int numInserted = getJdbiNumericQuestion().insert(questionDef.getQuestionId(), questionDef.getNumericType(), placeholderTemplateId);
        if (numInserted != 1) {
            throw new DaoException("Inserted " + numInserted + " for numeric question " + questionDef.getStableId());
        }
    }

    /**
     * Create new picklist question by inserting common data and picklist specific data.
     *
     * @param activityId the associated activity
     * @param picklist   the question definition, without generated things like ids
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertQuestion(long activityId, PicklistQuestionDef picklist, long revisionId) {
        if (picklist.hasNumOptionsSelectedRule()) {
            throw new UnsupportedOperationException("NUM_OPTIONS_SELECTED validation rule currently not supported");
        }

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
        getPicklistQuestionDao().insertOptions(picklist.getQuestionId(), picklist.getPicklistOptions(), revisionId);
    }

    default void insertQuestion(long activityId, CompositeQuestionDef compositeQuestion, long revisionId) {
        boolean acceptable = compositeQuestion.getChildren().stream().allMatch(child -> {
            QuestionType type = child.getQuestionType();
            return type == QuestionType.DATE || type == QuestionType.PICKLIST
                    || type == QuestionType.TEXT || type == QuestionType.NUMERIC;
        });
        if (!acceptable) {
            throw new DaoException("Composites only support DATE, PICKLIST, TEXT and NUMERIC child questions");
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
                buttonTemplateId, addItemTemplateId, compositeQuestion.getChildOrientation(), compositeQuestion.isUnwrapOnExport());
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
        ValidationDto validation = getJdbiQuestionValidation().getRequiredValidationIfActive(questionId)
                .orElseThrow(() -> new NoSuchElementException("Question does not have a required validation rule"));
        getValidationDao().disableBaseRule(validation, meta);
    }

    default QuestionBlockDef findBlockDefByBlockIdAndTimestamp(long blockId, long timestamp) {
        Long questionId = getJdbiBlockQuestion()
                .findQuestionIdsByBlockIdsAndTimestamp(Set.of(blockId), timestamp)
                .get(blockId);
        QuestionDef questionDef = Optional.ofNullable(questionId)
                .flatMap(id -> getJdbiQuestion().findQuestionDtoById(id))
                .map(dto -> findQuestionDefByDtoAndTimestamp(dto, timestamp))
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find question definition for block id %d and timestamp %d", blockId, timestamp)));
        return new QuestionBlockDef(questionDef);
    }

    default QuestionDef findQuestionDefByDtoAndTimestamp(QuestionDto questionDto, long timestamp) {
        QuestionDef questionDef;
        switch (questionDto.getType()) {
            case AGREEMENT:
                questionDef = findAgreementQuestionDefByDtoAndTimestamp(questionDto, timestamp);
                break;
            case BOOLEAN:
                questionDef = findBoolQuestionDefByDtoAndTimestamp((BooleanQuestionDto) questionDto, timestamp);
                break;
            case TEXT:
                questionDef = findTextQuestionDefByDtoAndTimestamp((TextQuestionDto) questionDto, timestamp);
                break;
            case DATE:
                questionDef = findDateQuestionDefByDtoAndTimestamp((DateQuestionDto) questionDto, timestamp);
                break;
            case NUMERIC:
                questionDef = findNumericQuestionDefByDtoAndTimestamp((NumericQuestionDto) questionDto, timestamp);
                break;
            case PICKLIST:
                questionDef = findPicklistQuestionDefByDtoAndTimestamp((PicklistQuestionDto) questionDto, timestamp);
                break;
            case COMPOSITE:
                questionDef = findCompositeQuestionDefByDtoAndTimestamp((CompositeQuestionDto) questionDto, timestamp);
                break;
            default:
                throw new DaoException("Unhandled question type " + questionDto.getType());
        }
        return questionDef;
    }

    default AgreementQuestionDef findAgreementQuestionDefByDtoAndTimestamp(QuestionDto questionDto, long timestamp) {
        TemplateDao templateDao = getTemplateDao();
        Template prompt = templateDao.loadTemplateById(questionDto.getPromptTemplateId());
        Template tooltipTemplate = questionDto.getTooltipTemplateId() == null ? null
                : templateDao.loadTemplateById(questionDto.getTooltipTemplateId());
        Template additionalInfoHeader = questionDto.getAdditionalInfoHeaderTemplateId() == null ? null
                : templateDao.loadTemplateById(questionDto.getAdditionalInfoHeaderTemplateId());
        Template additionalInfoFooter = questionDto.getAdditionalInfoFooterTemplateId() == null ? null
                : templateDao.loadTemplateById(questionDto.getAdditionalInfoFooterTemplateId());

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(questionDto.getId(), timestamp);

        AgreementQuestionDef def = new AgreementQuestionDef(
                questionDto.getStableId(),
                questionDto.isRestricted(),
                prompt,
                tooltipTemplate,
                additionalInfoHeader, additionalInfoFooter,
                validations,
                questionDto.shouldHideNumber(),
                questionDto.isWriteOnce());
        def.setDeprecated(questionDto.isDeprecated());
        def.setQuestionId(questionDto.getId());

        return def;
    }

    default BoolQuestionDef findBoolQuestionDefByDtoAndTimestamp(BooleanQuestionDto boolDto, long timestamp) {
        TemplateDao templateDao = getTemplateDao();
        Template prompt = templateDao.loadTemplateById(boolDto.getPromptTemplateId());
        Template tooltipTemplate = boolDto.getTooltipTemplateId() == null ? null
                : templateDao.loadTemplateById(boolDto.getTooltipTemplateId());
        Template additionalInfoHeader = boolDto.getAdditionalInfoHeaderTemplateId() == null ? null
                : templateDao.loadTemplateById(boolDto.getAdditionalInfoHeaderTemplateId());
        Template additionalInfoFooter = boolDto.getAdditionalInfoFooterTemplateId() == null ? null
                : templateDao.loadTemplateById(boolDto.getAdditionalInfoFooterTemplateId());
        Template trueTemplate = templateDao.loadTemplateById(boolDto.getTrueTemplateId());
        Template falseTemplate = templateDao.loadTemplateById(boolDto.getFalseTemplateId());

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(boolDto.getId(), timestamp);

        return BoolQuestionDef.builder(boolDto.getStableId(), prompt, trueTemplate, falseTemplate)
                .setQuestionId(boolDto.getId())
                .setTooltip(tooltipTemplate)
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setRestricted(boolDto.isRestricted())
                .setDeprecated(boolDto.isDeprecated())
                .setWriteOnce(boolDto.isWriteOnce())
                .setHideNumber(boolDto.shouldHideNumber())
                .addValidations(validations)
                .build();
    }

    default TextQuestionDef findTextQuestionDefByDtoAndTimestamp(TextQuestionDto textDto, long timestamp) {
        TemplateDao templateDao = getTemplateDao();
        Template prompt = templateDao.loadTemplateById(textDto.getPromptTemplateId());
        Template tooltipTemplate = textDto.getTooltipTemplateId() == null ? null
                : templateDao.loadTemplateById(textDto.getTooltipTemplateId());
        Template additionalInfoHeader = textDto.getAdditionalInfoHeaderTemplateId() == null ? null
                : templateDao.loadTemplateById(textDto.getAdditionalInfoHeaderTemplateId());
        Template additionalInfoFooter = textDto.getAdditionalInfoFooterTemplateId() == null ? null
                : templateDao.loadTemplateById(textDto.getAdditionalInfoFooterTemplateId());

        List<String> suggestions = new ArrayList<>();
        if (textDto.getSuggestionType() == SuggestionType.INCLUDED) {
            suggestions = getJdbiQuestion().findTextQuestionSuggestions(textDto.getId());
        }

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(textDto.getId(), timestamp);

        Template placeholderTemplate = null;
        if (textDto.getPlaceholderTemplateId() != null) {
            placeholderTemplate = templateDao.loadTemplateById(textDto.getPlaceholderTemplateId());
        }
        Template confirmPromptTemplate = null;
        if (textDto.getConfirmPromptTemplateId() != null) {
            confirmPromptTemplate = templateDao.loadTemplateById(textDto.getConfirmPromptTemplateId());
        }
        Template mismatchMessageTemplate = null;
        if (textDto.getMismatchMessageTemplateId() != null) {
            mismatchMessageTemplate = templateDao.loadTemplateById(textDto.getMismatchMessageTemplateId());
        }

        return TextQuestionDef.builder(textDto.getInputType(), textDto.getStableId(), prompt)
                .setSuggestionType(textDto.getSuggestionType())
                .setPlaceholderTemplate(placeholderTemplate)
                .setTooltip(tooltipTemplate)
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setQuestionId(textDto.getId())
                .setRestricted(textDto.isRestricted())
                .setDeprecated(textDto.isDeprecated())
                .setWriteOnce(textDto.isWriteOnce())
                .setHideNumber(textDto.shouldHideNumber())
                .setConfirmEntry(textDto.isConfirmEntry())
                .setConfirmPromptTemplate(confirmPromptTemplate)
                .setMismatchMessage(mismatchMessageTemplate)
                .addSuggestions(suggestions)
                .addValidations(validations)
                .build();
    }

    default DateQuestionDef findDateQuestionDefByDtoAndTimestamp(DateQuestionDto dateDto, long timestamp) {
        DatePicklistDef picklistDef = null;
        if (dateDto.getRenderMode() == DateRenderMode.PICKLIST) {
            picklistDef = dateDto.getPicklistDef();
        }

        List<DateFieldType> fields = dateDto.getFields();

        TemplateDao templateDao = getTemplateDao();
        Template prompt = templateDao.loadTemplateById(dateDto.getPromptTemplateId());
        Template tooltipTemplate = dateDto.getTooltipTemplateId() == null ? null
                : templateDao.loadTemplateById(dateDto.getTooltipTemplateId());
        Template additionalInfoHeader = dateDto.getAdditionalInfoHeaderTemplateId() == null ? null
                : templateDao.loadTemplateById(dateDto.getAdditionalInfoHeaderTemplateId());
        Template additionalInfoFooter = dateDto.getAdditionalInfoFooterTemplateId() == null ? null
                : templateDao.loadTemplateById(dateDto.getAdditionalInfoFooterTemplateId());
        Template placeholderTemplate = dateDto.getPlaceholderTemplateId() == null ? null
                : templateDao.loadTemplateById(dateDto.getPlaceholderTemplateId());

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(dateDto.getId(), timestamp);

        return DateQuestionDef.builder(dateDto.getRenderMode(), dateDto.getStableId(), prompt)
                .setDisplayCalendar(dateDto.shouldDisplayCalendar())
                .setPicklistDef(picklistDef)
                .setQuestionId(dateDto.getId())
                .setTooltip(tooltipTemplate)
                .setPlaceholderTemplate(placeholderTemplate)
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setRestricted(dateDto.isRestricted())
                .setDeprecated(dateDto.isDeprecated())
                .setWriteOnce(dateDto.isWriteOnce())
                .setHideNumber(dateDto.shouldHideNumber())
                .addValidations(validations)
                .addFields(fields)
                .build();
    }

    default QuestionDef findNumericQuestionDefByDtoAndTimestamp(NumericQuestionDto numericDto, long timestamp) {
        TemplateDao templateDao = getTemplateDao();
        Template prompt = templateDao.loadTemplateById(numericDto.getPromptTemplateId());
        Template tooltipTemplate = numericDto.getTooltipTemplateId() == null
                ? null : templateDao.loadTemplateById(numericDto.getTooltipTemplateId());
        Template additionalInfoHeader = (numericDto.getAdditionalInfoHeaderTemplateId() == null)
                ? null : templateDao.loadTemplateById(numericDto.getAdditionalInfoHeaderTemplateId());
        Template additionalInfoFooter = (numericDto.getAdditionalInfoFooterTemplateId() == null)
                ? null : templateDao.loadTemplateById(numericDto.getAdditionalInfoFooterTemplateId());
        Template placeholderTemplate = (numericDto.getPlaceholderTemplateId() == null)
                ? null : templateDao.loadTemplateById(numericDto.getPlaceholderTemplateId());

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(numericDto.getId(), timestamp);

        return NumericQuestionDef
                .builder(numericDto.getNumericType(), numericDto.getStableId(), prompt)
                .setPlaceholderTemplate(placeholderTemplate)
                .setTooltip(tooltipTemplate)
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setQuestionId(numericDto.getId())
                .setRestricted(numericDto.isRestricted())
                .setDeprecated(numericDto.isDeprecated())
                .setWriteOnce(numericDto.isWriteOnce())
                .setHideNumber(numericDto.shouldHideNumber())
                .addValidations(validations)
                .build();
    }

    default PicklistQuestionDef findPicklistQuestionDefByDtoAndTimestamp(PicklistQuestionDto picklistDto, long timestamp) {
        PicklistQuestionDao.GroupAndOptionDtos container = getPicklistQuestionDao()
                .findOrderedGroupAndOptionDtos(picklistDto.getId(), timestamp);

        TemplateDao templateDao = getTemplateDao();
        List<PicklistGroupDef> groups = new ArrayList<>();
        for (PicklistGroupDto dto : container.getGroups()) {
            Template nameTemplate = templateDao.loadTemplateById(dto.getNameTemplateId());
            List<PicklistOptionDef> options = container.getGroupIdToOptions().get(dto.getId())
                    .stream().map(optionDto -> {
                        Template optionLabel = templateDao.loadTemplateById(optionDto.getOptionLabelTemplateId());
                        Template detailLabel = !optionDto.getAllowDetails() ? null
                                : templateDao.loadTemplateById(optionDto.getDetailLabelTemplateId());
                        Template tooltipTemplate = optionDto.getTooltipTemplateId() == null ? null
                                : templateDao.loadTemplateById(optionDto.getTooltipTemplateId());
                        return new PicklistOptionDef(optionDto.getId(), optionDto.getStableId(),
                                optionLabel, tooltipTemplate, detailLabel, optionDto.isExclusive());
                    })
                    .collect(Collectors.toList());
            groups.add(new PicklistGroupDef(dto.getId(), dto.getStableId(), nameTemplate, options));
        }

        List<PicklistOptionDef> ungroupedOptions = container.getUngroupedOptions()
                .stream().map(optionDto -> {
                    Template optionLabel = templateDao.loadTemplateById(optionDto.getOptionLabelTemplateId());
                    Template detailLabel = !optionDto.getAllowDetails() ? null
                            : templateDao.loadTemplateById(optionDto.getDetailLabelTemplateId());
                    Template tooltipTemplate = optionDto.getTooltipTemplateId() == null ? null
                            : templateDao.loadTemplateById(optionDto.getTooltipTemplateId());
                    Template nestedOptionsTemplate = optionDto.getNestedOptionsTemplateId() == null ? null
                            : templateDao.loadTemplateById(optionDto.getNestedOptionsTemplateId());

                    PicklistOptionDef optionDef = null;
                    if (CollectionUtils.isEmpty(optionDto.getNestedOptions())) {
                        optionDef = new PicklistOptionDef(optionDto.getId(), optionDto.getStableId(),
                                optionLabel, tooltipTemplate, detailLabel, optionDto.isExclusive());
                    } else {
                        if (CollectionUtils.isNotEmpty(optionDto.getNestedOptions())) {
                            List<PicklistOptionDef> nestedOptions = new ArrayList<>();
                            for (PicklistOptionDto nestedOptionDto : optionDto.getNestedOptions()) {
                                Template nestedOptionLabel = templateDao.loadTemplateById(nestedOptionDto.getOptionLabelTemplateId());
                                Template nestedDetailLabel = !nestedOptionDto.getAllowDetails() ? null
                                        : templateDao.loadTemplateById(nestedOptionDto.getDetailLabelTemplateId());
                                Template nestedTooltipTemplate = nestedOptionDto.getTooltipTemplateId() == null ? null
                                        : templateDao.loadTemplateById(nestedOptionDto.getTooltipTemplateId());
                                nestedOptions.add(new PicklistOptionDef(nestedOptionDto.getId(), nestedOptionDto.getStableId(),
                                        nestedOptionLabel, nestedTooltipTemplate, nestedDetailLabel,
                                        nestedOptionDto.isExclusive()));
                            }
                            optionDef = new PicklistOptionDef(optionDto.getId(), optionDto.getStableId(),
                                    optionLabel, tooltipTemplate, detailLabel, optionDto.isExclusive(),
                                    nestedOptionsTemplate, nestedOptions);
                        }
                    }
                    return optionDef;
                })
                .collect(Collectors.toList());

        Template prompt = templateDao.loadTemplateById(picklistDto.getPromptTemplateId());
        Template tooltipTemplate = picklistDto.getTooltipTemplateId() == null ? null
                : templateDao.loadTemplateById(picklistDto.getTooltipTemplateId());
        Template additionalInfoHeader = picklistDto.getAdditionalInfoHeaderTemplateId() == null ? null
                : templateDao.loadTemplateById(picklistDto.getAdditionalInfoHeaderTemplateId());
        Template additionalInfoFooter = picklistDto.getAdditionalInfoFooterTemplateId() == null ? null
                : templateDao.loadTemplateById(picklistDto.getAdditionalInfoFooterTemplateId());

        Template label = null;
        if (picklistDto.getRenderMode() == PicklistRenderMode.DROPDOWN) {
            label = templateDao.loadTemplateById(picklistDto.getLabelTemplateId());
        }

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(picklistDto.getId(), timestamp);

        return PicklistQuestionDef.builder(picklistDto.getSelectMode(), picklistDto.getRenderMode(), picklistDto.getStableId(), prompt)
                .setLabel(label)
                .setQuestionId(picklistDto.getId())
                .setTooltip(tooltipTemplate)
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setRestricted(picklistDto.isRestricted())
                .setDeprecated(picklistDto.isDeprecated())
                .setWriteOnce(picklistDto.isWriteOnce())
                .setHideNumber(picklistDto.shouldHideNumber())
                .addValidations(validations)
                .addGroups(groups)
                .addOptions(ungroupedOptions)
                .build();
    }

    default CompositeQuestionDef findCompositeQuestionDefByDtoAndTimestamp(CompositeQuestionDto compositeDto, long timestamp) {
        JdbiQuestion jdbiQuestion = getJdbiQuestion();
        List<Long> childIds = jdbiQuestion.findCompositeChildIdsByParentId(compositeDto.getId());
        Map<Long, QuestionDef> childrenDefs;
        try (var stream = jdbiQuestion.findQuestionDtosByIds(childIds)) {
            childrenDefs = stream
                    .map(child -> findQuestionDefByDtoAndTimestamp(child, timestamp))
                    .collect(Collectors.toMap(QuestionDef::getQuestionId, Function.identity()));
        }
        List<QuestionDef> orderedChildDefs = childIds.stream()
                .map(childrenDefs::get)
                .collect(Collectors.toList());

        TemplateDao templateDao = getTemplateDao();
        Template prompt = templateDao.loadTemplateById(compositeDto.getPromptTemplateId());
        Template tooltipTemplate = compositeDto.getTooltipTemplateId() == null ? null
                : templateDao.loadTemplateById(compositeDto.getTooltipTemplateId());
        Template additionalInfoHeader = compositeDto.getAdditionalInfoHeaderTemplateId() == null ? null
                : templateDao.loadTemplateById(compositeDto.getAdditionalInfoHeaderTemplateId());
        Template additionalInfoFooter = compositeDto.getAdditionalInfoFooterTemplateId() == null ? null
                : templateDao.loadTemplateById(compositeDto.getAdditionalInfoFooterTemplateId());

        Template buttonTmpl = null;
        if (compositeDto.getAddButtonTemplateId() != null) {
            buttonTmpl = templateDao.loadTemplateById(compositeDto.getAddButtonTemplateId());
        }

        Template addItemTmpl = null;
        if (compositeDto.getAdditionalItemTemplateId() != null) {
            addItemTmpl = templateDao.loadTemplateById(compositeDto.getAdditionalItemTemplateId());
        }

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(compositeDto.getId(), timestamp);

        return CompositeQuestionDef.builder()
                .setStableId(compositeDto.getStableId())
                .setPrompt(prompt)
                .setAllowMultiple(compositeDto.isAllowMultiple())
                .setUnwrapOnExport(compositeDto.isUnwrapOnExport())
                .setAddButtonTemplate(buttonTmpl)
                .setAdditionalItemTemplate(addItemTmpl)
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setTooltip(tooltipTemplate)
                .setQuestionId(compositeDto.getId())
                .setRestricted(compositeDto.isRestricted())
                .setDeprecated(compositeDto.isDeprecated())
                .setWriteOnce(compositeDto.isWriteOnce())
                .setHideNumber(compositeDto.shouldHideNumber())
                .addValidations(validations)
                .addChildrenQuestions(orderedChildDefs)
                .setChildOrientation(compositeDto.getChildOrientation())
                .build();
    }
}
