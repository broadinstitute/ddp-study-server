package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
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
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface QuestionDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(QuestionDao.class);

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

    int DISPLAY_ORDER_GAP = 10;

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
                .getQuestionDto(blockId, activityInstanceGuid)
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
     * Get the control question for a conditional block.
     * This allows fetching deprecated control questions. Prefer the other method that excludes them.
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
                .findControlQuestionDto(blockId, activityInstanceGuid)
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
        return getJdbiQuestion().getQuestionDtoIfActive(questionId)
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

        List<Rule> untypedRules = getValidationDao().getValidationRules(dto.getId(), langCodeId);

        Question question;

        switch (dto.getType()) {
            case BOOLEAN:
                question = getBooleanQuestion(dto, answerIds, untypedRules);
                break;
            case PICKLIST:
                question = getPicklistQuestion(dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case TEXT:
                question = getTextQuestion(dto, activityInstanceGuid, answerIds, untypedRules);
                break;
            case DATE:
                question = getDateQuestion(dto, answerIds, untypedRules);
                break;
            case NUMERIC:
                question = getNumericQuestion(dto, answerIds, untypedRules);
                break;
            case AGREEMENT:
                question = getAgreementQuestion(dto, answerIds, untypedRules);
                break;
            case COMPOSITE:
                question = getCompositeQuestion(dto, activityInstanceGuid, answerIds, untypedRules, langCodeId);
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
     * @param dto          the question dto
     * @param answerIds    list of base answer ids to question (may be empty)
     * @param untypedRules list of untyped validations for question (may be empty)
     * @return boolean question object
     */
    default BoolQuestion getBooleanQuestion(QuestionDto dto,
                                            List<Long> answerIds,
                                            List<Rule> untypedRules) {
        BooleanQuestionDto booleanQuestionDto = getJdbiBooleanQuestion()
                .findDtoByQuestionId(dto.getId())
                .orElseThrow(() -> new DaoException("Could not find boolean question for id " + dto.getId()));

        AnswerDao answerDao = getAnswerDao();
        List<BoolAnswer> boolAnswers = answerIds.stream()
                .map(answerId -> (BoolAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find boolean answer with id " + answerId)))
                .collect(toList());

        List<Rule<BoolAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<BoolAnswer>) rule)
                .collect(toList());

        return new BoolQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.isRestricted(), dto.isDeprecated(),
                dto.getAdditionalInfoHeaderTemplateId(), dto.getAdditionalInfoFooterTemplateId(),
                boolAnswers, rules, booleanQuestionDto.getTrueTemplateId(),
                booleanQuestionDto.getFalseTemplateId());
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
    default PicklistQuestion getPicklistQuestion(QuestionDto dto,
                                                 String activityInstanceGuid,
                                                 List<Long> answerIds,
                                                 List<Rule> untypedRules) {
        PicklistQuestionDto picklistQuestionDto = getJdbiPicklistQuestion()
                .findDtoByQuestionId(dto.getId())
                .orElseThrow(() -> new DaoException("Could not find picklist question for id " + dto.getId()));

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

        // Put the ungrouped options in the list first.
        for (PicklistOptionDto optionDto : container.getUngroupedOptions()) {
            allOptions.add(new PicklistOption(optionDto.getStableId(),
                    optionDto.getOptionLabelTemplateId(), optionDto.getDetailLabelTemplateId(),
                    optionDto.getAllowDetails(), optionDto.isExclusive()));
        }

        // Then options from groups.
        for (PicklistGroupDto groupDto : container.getGroups()) {
            groups.add(new PicklistGroup(groupDto.getStableId(), groupDto.getNameTemplateId()));
            List<PicklistOptionDto> optionDtos = container.getGroupIdToOptions().get(groupDto.getId());
            for (PicklistOptionDto optionDto : optionDtos) {
                allOptions.add(new PicklistOption(groupDto.getStableId(), optionDto.getStableId(),
                        optionDto.getOptionLabelTemplateId(), optionDto.getDetailLabelTemplateId(),
                        optionDto.getAllowDetails(), optionDto.isExclusive()));
            }
        }

        return new PicklistQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.isRestricted(), dto.isDeprecated(),
                dto.getAdditionalInfoHeaderTemplateId(), dto.getAdditionalInfoFooterTemplateId(),
                picklistAnswers, rules,
                picklistQuestionDto.getSelectMode(),
                picklistQuestionDto.getRenderMode(),
                picklistQuestionDto.getLabelTemplateId(),
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
    default TextQuestion getTextQuestion(QuestionDto dto,
                                         String activityInstanceGuid,
                                         List<Long> answerIds,
                                         List<Rule> untypedRules) {
        TextQuestionDto textQuestionDto = getJdbiTextQuestion()
                .findDtoByQuestionId(dto.getId())
                .orElseThrow(() -> new DaoException("Could not find text question for id " + dto.getId()));

        AnswerDao answerDao = getAnswerDao();
        List<TextAnswer> textAnswers = answerIds.stream()
                .map(answerId -> (TextAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find text answer with id " + answerId)))
                .collect(toList());

        List<Rule<TextAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<TextAnswer>) rule)
                .collect(toList());

        return new TextQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                textQuestionDto.getPlaceholderTemplateId(),
                textQuestionDto.isRestricted(),
                textQuestionDto.isDeprecated(),
                textQuestionDto.getAdditionalInfoHeaderTemplateId(),
                textQuestionDto.getAdditionalInfoFooterTemplateId(),
                textAnswers,
                rules,
                textQuestionDto.getInputType(),
                textQuestionDto.getSuggestionType(),
                textQuestionDto.getSuggestions(),
                textQuestionDto.isConfirmEntry(),
                textQuestionDto.getConfirmPromptTemplateId(),
                textQuestionDto.getMismatchMessageTemplateId());
    }

    /**
     * Build a date question.
     *
     * @param dto          the question dto
     * @param answerIds    list of base answer ids to question (may be empty)
     * @param untypedRules list of untyped validations for question (may be empty)
     * @return date question object
     */
    default DateQuestion getDateQuestion(QuestionDto dto,
                                         List<Long> answerIds,
                                         List<Rule> untypedRules) {
        DateQuestionDto dateQuestionDto = getJdbiDateQuestion()
                .findDtoByQuestionId(dto.getId())
                .orElseThrow(() -> new DaoException("Could not find date question for id " + dto.getId()));

        AnswerDao answerDao = getAnswerDao();
        List<DateAnswer> dateAnswers = answerIds.stream()
                .map(answerId -> (DateAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find date answer with id " + answerId)))
                .collect(toList());

        List<Rule<DateAnswer>> rules = untypedRules
                .stream()
                .map(rule -> (Rule<DateAnswer>) rule)
                .collect(toList());

        List<DateFieldType> fieldTypes = getHandle()
                .attach(JdbiDateQuestionFieldOrder.class)
                .getOrderedFieldsByQuestionId(dto.getId());
        if (fieldTypes.isEmpty()) {
            throw new DaoException("Date question should have at least one date field but none found for question id "
                    + dto.getId());
        }

        if (dateQuestionDto.getRenderMode().equals(DateRenderMode.PICKLIST)) {
            DatePicklistDef config = getJdbiDateQuestion().getDatePicklistDefByQuestionId(dto.getId()).get();
            return new DatePicklistQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                    dto.isRestricted(), dto.isDeprecated(),
                    dto.getAdditionalInfoHeaderTemplateId(), dto.getAdditionalInfoFooterTemplateId(),
                    dateAnswers, rules, dateQuestionDto.getRenderMode(), dateQuestionDto.shouldDisplayCalendar(),
                    fieldTypes, dateQuestionDto.getPlaceholderTemplateId(), config.getUseMonthNames(), config.getStartYear(),
                    config.getEndYear(), config.getFirstSelectedYear());
        } else {
            return new DateQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                    dto.isRestricted(), dto.isDeprecated(),
                    dto.getAdditionalInfoHeaderTemplateId(),
                    dto.getAdditionalInfoFooterTemplateId(),
                    dateAnswers,
                    rules,
                    dateQuestionDto.getRenderMode(),
                    dateQuestionDto.shouldDisplayCalendar(),
                    fieldTypes,
                    dateQuestionDto.getPlaceholderTemplateId());
        }
    }

    /**
     * Build a numeric question.
     *
     * @param dto          the question dto
     * @param answerIds    list of base answer ids to question (may be empty)
     * @param untypedRules list of untyped validations for question (may be empty)
     * @return numeric question object
     */
    default Question getNumericQuestion(QuestionDto dto, List<Long> answerIds, List<Rule> untypedRules) {
        NumericQuestionDto numericQuestionDto = getJdbiNumericQuestion().findDtoByQuestionId(dto.getId())
                .orElseThrow(() -> new DaoException("Could not find numeric question with id " + dto.getId()));

        AnswerDao answerDao = getAnswerDao();
        List<NumericAnswer> answers = answerIds.stream()
                .map(answerId -> (NumericAnswer) answerDao.findAnswerById(answerId)
                        .orElseThrow(() -> new DaoException("Could not find numeric answer with id " + answerId)))
                .collect(toList());

        List<Rule<NumericAnswer>> rules = untypedRules.stream()
                .map(rule -> (Rule<NumericAnswer>) rule)
                .collect(toList());

        return new NumericQuestion(
                dto.getStableId(),
                dto.getPromptTemplateId(),
                numericQuestionDto.getPlaceholderTemplateId(),
                dto.isRestricted(),
                dto.isDeprecated(),
                dto.getAdditionalInfoHeaderTemplateId(),
                dto.getAdditionalInfoFooterTemplateId(),
                answers,
                rules,
                numericQuestionDto.getNumericType());
    }

    /**
     * Build a agreement question.
     *
     * @param dto          the question dto
     * @param answerIds    list of base answer ids to question (may be empty)
     * @param untypedRules list of untyped validations for question (may be empty)
     * @return agreement question object
     */
    default AgreementQuestion getAgreementQuestion(QuestionDto dto,
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

        return new AgreementQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.isRestricted(), dto.isDeprecated(),
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
    default CompositeQuestion getCompositeQuestion(QuestionDto dto,
                                                   String activityInstanceGuid,
                                                   List<Long> answerIds,
                                                   List<Rule> untypedRules,
                                                   long langCodeId) {
        CompositeQuestionDto compositeQuestionDto = getJdbiCompositeQuestion()
                .findDtoByQuestionId(dto.getId())
                .orElseThrow(() -> new DaoException("Could not find composite question using question id " + dto.getId()));

        boolean retrieveChildAnswers = true;
        List<Question> childQuestions = compositeQuestionDto.getChildQuestions().stream()
                .map(childQuestionDto ->
                        getQuestionByActivityInstanceAndDto(childQuestionDto, activityInstanceGuid, retrieveChildAnswers, langCodeId))
                .collect(toList());

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

        return new CompositeQuestion(dto.getStableId(), dto.getPromptTemplateId(),
                dto.isRestricted(), dto.isDeprecated(),
                dto.getAdditionalInfoHeaderTemplateId(), dto.getAdditionalInfoFooterTemplateId(),
                rules, compositeQuestionDto.isAllowMultiple(), compositeQuestionDto.isUnwrapOnExport(),
                compositeQuestionDto.getAddButtonTemplateId(), compositeQuestionDto.getAdditionalItemTemplateId(),
                childQuestions, compositeQuestionDto.getChildOrientation(), compositeAnswers);
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
            return type == QuestionType.DATE || type == QuestionType.PICKLIST || type == QuestionType.TEXT;
        });
        if (!acceptable) {
            throw new DaoException("Composites only support DATE, PICKLIST, and TEXT child questions");
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

        BooleanQuestionDto booleanQuestion = getJdbiBooleanQuestion().findDtoByQuestionId(questionId).orElse(null);
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
        TextQuestionDto question = getJdbiTextQuestion().findDtoByQuestionId(questionId).orElse(null);
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
        QuestionDto question = getJdbiQuestion().getQuestionDtoById(questionId).orElse(null);
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
        NumericQuestionDto questionDto = getJdbiNumericQuestion().findDtoByQuestionId(questionId).orElse(null);
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

        PicklistQuestionDto picklistQuestion = getJdbiPicklistQuestion().findDtoByQuestionId(questionId).orElse(null);
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
     * End currently active agreement question by terminating common data and agreement question specific data
     * Since the agreement question doesn't have any specific data so far, it boils down to the former
     */
    default void disableAgreementQuestion(long questionId, RevisionMetadata meta) {
        AgreementQuestionDto questionDto = getJdbiAgreementQuestion().findDtoByQuestionId(questionId).orElse(null);
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
        QuestionDef questionDef = getJdbiBlockQuestion()
                .findQuestionDtoByBlockIdAndTimestamp(blockId, timestamp)
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
                questionDef = findBoolQuestionDefByDtoAndTimestamp(questionDto, timestamp);
                break;
            case TEXT:
                questionDef = findTextQuestionDefByDtoAndTimestamp(questionDto, timestamp);
                break;
            case DATE:
                questionDef = findDateQuestionDefByDtoAndTimestamp(questionDto, timestamp);
                break;
            case NUMERIC:
                questionDef = findNumericQuestionDefByDtoAndTimestamp(questionDto, timestamp);
                break;
            case PICKLIST:
                questionDef = findPicklistQuestionDefByDtoAndTimestamp(questionDto, timestamp);
                break;
            case COMPOSITE:
                questionDef = findCompositeQuestionDefByDtoAndTimestamp(questionDto, timestamp);
                break;
            default:
                throw new DaoException("Unhandled question type " + questionDto.getType());
        }
        return questionDef;
    }

    default AgreementQuestionDef findAgreementQuestionDefByDtoAndTimestamp(QuestionDto questionDto, long timestamp) {

        Template prompt = getTemplateDao().loadTemplateById(questionDto.getPromptTemplateId());
        Template additionalInfoHeader = Template.text("");
        Template additionalInfoFooter = Template.text("");

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(questionDto.getId(), timestamp);

        AgreementQuestionDef def = new AgreementQuestionDef(
                questionDto.getStableId(),
                questionDto.isRestricted(),
                prompt,
                additionalInfoHeader, additionalInfoFooter,
                validations,
                questionDto.shouldHideNumber());
        def.setDeprecated(questionDto.isDeprecated());
        def.setQuestionId(questionDto.getId());

        return def;
    }

    default BoolQuestionDef findBoolQuestionDefByDtoAndTimestamp(QuestionDto questionDto, long timestamp) {
        Template prompt = getTemplateDao().loadTemplateById(questionDto.getPromptTemplateId());
        Template additionalInfoHeader = Template.text("");
        Template additionalInfoFooter = Template.text("");

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(questionDto.getId(), timestamp);

        return BoolQuestionDef.builder(questionDto.getStableId(), prompt, Template.text("yes"), Template.text("no"))
                .setQuestionId(questionDto.getId())
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setRestricted(questionDto.isRestricted())
                .setDeprecated(questionDto.isDeprecated())
                .setHideNumber(questionDto.shouldHideNumber())
                .addValidations(validations)
                .build();
    }

    default TextQuestionDef findTextQuestionDefByDtoAndTimestamp(QuestionDto questionDto, long timestamp) {
        TextQuestionDto textDto = getJdbiTextQuestion().findDtoByQuestionId(questionDto.getId())
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find text question dto with question id=%d while querying text question definition",
                        questionDto.getId())));

        Template prompt = getTemplateDao().loadTemplateById(questionDto.getPromptTemplateId());
        Template additionalInfoHeader = Template.text("");
        Template additionalInfoFooter = Template.text("");

        //query suggestions
        List<String> suggestions = getJdbiTextQuestionSuggestion().getTextQuestionSuggestions(textDto.getId());

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(textDto.getId(), timestamp);

        Template confirmPromptTemplate = null;
        if (textDto.getConfirmPromptTemplateId() != null) {
            confirmPromptTemplate = getTemplateDao().loadTemplateById(textDto.getConfirmPromptTemplateId());
        }
        Template mismatchMessageTemplate = null;
        if (textDto.getMismatchMessageTemplateId() != null) {
            mismatchMessageTemplate = getTemplateDao().loadTemplateById(textDto.getMismatchMessageTemplateId());
        }
        return TextQuestionDef.builder(textDto.getInputType(), textDto.getStableId(), prompt)
                .setSuggestionType(textDto.getSuggestionType())
                .setPlaceholderTemplate(Template.text("placeholder"))
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setQuestionId(textDto.getId())
                .setRestricted(textDto.isRestricted())
                .setDeprecated(textDto.isDeprecated())
                .setHideNumber(textDto.shouldHideNumber())
                .setConfirmEntry(textDto.isConfirmEntry())
                .setConfirmPromptTemplate(confirmPromptTemplate)
                .setMismatchMessage(mismatchMessageTemplate)
                .addSuggestions(suggestions)
                .addValidations(validations)
                .build();
    }

    default DateQuestionDef findDateQuestionDefByDtoAndTimestamp(QuestionDto questionDto, long timestamp) {
        DateQuestionDto dateDto = getJdbiDateQuestion()
                .findDtoByQuestionId(questionDto.getId())
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find date question definition for id %d and timestamp %d", questionDto.getId(), timestamp)));

        DatePicklistDef picklistDef = null;
        if (dateDto.getRenderMode() == DateRenderMode.PICKLIST) {
            picklistDef = getJdbiDateQuestion()
                    .getDatePicklistDefByQuestionId(questionDto.getId())
                    .orElse(null);
        }

        List<DateFieldType> fields = getJdbiDateQuestionFieldOrder()
                .getOrderedFieldsByQuestionId(questionDto.getId());

        Template prompt = getTemplateDao().loadTemplateById(questionDto.getPromptTemplateId());
        Template additionalInfoHeader = Template.text("");
        Template additionalInfoFooter = Template.text("");

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(questionDto.getId(), timestamp);

        return DateQuestionDef.builder(dateDto.getRenderMode(), questionDto.getStableId(), prompt)
                .setDisplayCalendar(dateDto.shouldDisplayCalendar())
                .setPicklistDef(picklistDef)
                .setQuestionId(questionDto.getId())
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setRestricted(questionDto.isRestricted())
                .setDeprecated(questionDto.isDeprecated())
                .setHideNumber(questionDto.shouldHideNumber())
                .addValidations(validations)
                .addFields(fields)
                .build();
    }

    default QuestionDef findNumericQuestionDefByDtoAndTimestamp(QuestionDto questionDto, long timestamp) {
        NumericQuestionDto numericQuestionDto = getJdbiNumericQuestion().findDtoByQuestionId(questionDto.getId())
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find numeric question dto with question id %d while querying numeric question definition",
                        questionDto.getId())));

        TemplateDao templateDao = getTemplateDao();
        Template prompt = templateDao.loadTemplateById(questionDto.getPromptTemplateId());
        Template additionalInfoHeader = (questionDto.getAdditionalInfoHeaderTemplateId() == null)
                ? null : templateDao.loadTemplateById(questionDto.getAdditionalInfoHeaderTemplateId());
        Template additionalInfoFooter = (questionDto.getAdditionalInfoFooterTemplateId() == null)
                ? null : templateDao.loadTemplateById(questionDto.getAdditionalInfoFooterTemplateId());
        Template placeholderTemplate = (numericQuestionDto.getPlaceholderTemplateId() == null)
                ? null : templateDao.loadTemplateById(numericQuestionDto.getPlaceholderTemplateId());

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(questionDto.getId(), timestamp);

        return NumericQuestionDef
                .builder(numericQuestionDto.getNumericType(), questionDto.getStableId(), prompt)
                .setPlaceholderTemplate(placeholderTemplate)
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setQuestionId(questionDto.getId())
                .setRestricted(questionDto.isRestricted())
                .setDeprecated(questionDto.isDeprecated())
                .setHideNumber(questionDto.shouldHideNumber())
                .addValidations(validations)
                .build();
    }

    default PicklistQuestionDef findPicklistQuestionDefByDtoAndTimestamp(QuestionDto questionDto, long timestamp) {
        PicklistQuestionDto picklistDto = getJdbiPicklistQuestion()
                .findDtoByQuestionId(questionDto.getId())
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find picklist question definition for id %d and timestamp %d", questionDto.getId(), timestamp)));

        PicklistQuestionDao.GroupAndOptionDtos container = getPicklistQuestionDao()
                .findOrderedGroupAndOptionDtos(questionDto.getId(), timestamp);

        List<PicklistGroupDef> groups = new ArrayList<>();
        for (PicklistGroupDto dto : container.getGroups()) {
            Template nameTemplate = getTemplateDao().loadTemplateById(dto.getNameTemplateId());
            List<PicklistOptionDef> options = container.getGroupIdToOptions().get(dto.getId())
                    .stream().map(optionDto -> {
                        Template optionLabel = getTemplateDao().loadTemplateById(optionDto.getOptionLabelTemplateId());
                        Template detailLabel = optionDto.getAllowDetails() ? Template.text("") : null;
                        return new PicklistOptionDef(optionDto.getId(), optionDto.getStableId(),
                                optionLabel, detailLabel, optionDto.isExclusive());
                    })
                    .collect(Collectors.toList());
            groups.add(new PicklistGroupDef(dto.getId(), dto.getStableId(), nameTemplate, options));
        }

        List<PicklistOptionDef> ungroupedOptions = container.getUngroupedOptions()
                .stream().map(optionDto -> {
                    Template optionLabel = getTemplateDao().loadTemplateById(optionDto.getOptionLabelTemplateId());
                    Template detailLabel = optionDto.getAllowDetails() ? Template.text("") : null;
                    return new PicklistOptionDef(optionDto.getId(), optionDto.getStableId(),
                            optionLabel, detailLabel, optionDto.isExclusive());
                })
                .collect(Collectors.toList());

        Template prompt = getTemplateDao().loadTemplateById(questionDto.getPromptTemplateId());
        Template additionalInfoHeader = Template.text("");
        Template additionalInfoFooter = Template.text("");

        Template label = null;
        if (picklistDto.getRenderMode() == PicklistRenderMode.DROPDOWN) {
            label = Template.text("");
        }

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(questionDto.getId(), timestamp);

        return PicklistQuestionDef.builder(picklistDto.getSelectMode(), picklistDto.getRenderMode(), questionDto.getStableId(), prompt)
                .setLabel(label)
                .setQuestionId(questionDto.getId())
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setRestricted(questionDto.isRestricted())
                .setDeprecated(questionDto.isDeprecated())
                .setHideNumber(questionDto.shouldHideNumber())
                .addValidations(validations)
                .addGroups(groups)
                .addOptions(ungroupedOptions)
                .build();
    }

    default CompositeQuestionDef findCompositeQuestionDefByDtoAndTimestamp(QuestionDto questionDto, long timestamp) {
        CompositeQuestionDto compositeDto = getJdbiCompositeQuestion()
                .findDtoByQuestionId(questionDto.getId())
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find composite question definition for id %d and timestamp %d", questionDto.getId(), timestamp)));

        List<QuestionDef> childrenDefs = compositeDto.getChildQuestions()
                .stream().map(child -> findQuestionDefByDtoAndTimestamp(child, timestamp))
                .collect(toList());

        Template prompt = getTemplateDao().loadTemplateById(questionDto.getPromptTemplateId());
        Template additionalInfoHeader = Template.text("");
        Template additionalInfoFooter = Template.text("");

        Template buttonTmpl = null;
        if (compositeDto.getAddButtonTemplateId() != null) {
            buttonTmpl = Template.text("");
        }

        Template addItemTmpl = null;
        if (compositeDto.getAdditionalItemTemplateId() != null) {
            addItemTmpl = Template.text("");
        }

        List<RuleDef> validations = getValidationDao()
                .findRuleDefsByQuestionIdAndTimestamp(questionDto.getId(), timestamp);

        return CompositeQuestionDef.builder()
                .setStableId(questionDto.getStableId())
                .setPrompt(prompt)
                .setAllowMultiple(compositeDto.isAllowMultiple())
                .setUnwrapOnExport(compositeDto.isUnwrapOnExport())
                .setAddButtonTemplate(buttonTmpl)
                .setAdditionalItemTemplate(addItemTmpl)
                .setAdditionalInfoHeader(additionalInfoHeader)
                .setAdditionalInfoFooter(additionalInfoFooter)
                .setQuestionId(questionDto.getId())
                .setRestricted(questionDto.isRestricted())
                .setDeprecated(questionDto.isDeprecated())
                .setHideNumber(questionDto.shouldHideNumber())
                .addValidations(validations)
                .addChildrenQuestions(childrenDefs)
                .setChildOrientation(compositeDto.getChildOrientation())
                .build();
    }
}
