package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.validation.LengthDto;
import org.broadinstitute.ddp.db.dto.validation.NumOptionsSelectedDto;
import org.broadinstitute.ddp.db.dto.validation.RegexDto;
import org.broadinstitute.ddp.db.dto.validation.ValidationDto;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.validation.AgeRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.CompleteRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.IntRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.NumOptionsSelectedRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RegexRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.instance.validation.CompleteRule;
import org.broadinstitute.ddp.model.activity.instance.validation.DateFieldRequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.LengthRule;
import org.broadinstitute.ddp.model.activity.instance.validation.NumOptionsSelectedRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RegexRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ValidationDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(ValidationDao.class);

    @CreateSqlObject
    JdbiValidation getJdbiValidation();

    @CreateSqlObject
    JdbiValidationType getJdbiValidationType();

    @CreateSqlObject
    JdbiRegexValidation getJdbiRegexValidation();

    @CreateSqlObject
    JdbiLengthValidation getJdbiLengthValidation();

    @CreateSqlObject
    JdbiNumOptionsSelectedValidation getJdbiNumOptionsSelectedValidation();

    @CreateSqlObject
    JdbiDateRangeValidation getJdbiDateRangeValidation();

    @CreateSqlObject
    JdbiAgeRangeValidation getJdbiAgeRangeValidation();

    @CreateSqlObject
    JdbiIntRangeValidation getJdbiIntRangeValidation();

    @CreateSqlObject
    JdbiQuestionValidation getJdbiQuestionValidation();

    @CreateSqlObject
    JdbiRevision getJdbiRevision();

    @CreateSqlObject
    TemplateDao getTemplateDao();

    @CreateSqlObject
    JdbiI18nValidationMsgTrans getJdbiI18nValidationMsgTrans();


    /**
     * Create new validation rules for given question by inserting common data and then rule specific data.
     *
     * @param question   the associated question with validations
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertValidations(QuestionDef question, long revisionId) {
        long questionId = question.getQuestionId();
        for (RuleDef rule : question.getValidations()) {
            if (rule instanceof RequiredRuleDef) {
                insert(questionId, (RequiredRuleDef) rule, revisionId);
            } else if (rule instanceof CompleteRuleDef) {
                insert(questionId, (CompleteRuleDef) rule, revisionId);
            } else if (rule instanceof RegexRuleDef) {
                insert(questionId, (RegexRuleDef) rule, revisionId);
            } else if (rule instanceof LengthRuleDef) {
                insert(questionId, (LengthRuleDef) rule, revisionId);
            } else if (rule instanceof NumOptionsSelectedRuleDef) {
                insert(questionId, (NumOptionsSelectedRuleDef) rule, revisionId);
            } else if (rule instanceof DateFieldRequiredRuleDef) {
                insert(questionId, (DateFieldRequiredRuleDef) rule, revisionId);
            } else if (rule instanceof DateRangeRuleDef) {
                insert(questionId, (DateRangeRuleDef) rule, revisionId);
            } else if (rule instanceof AgeRangeRuleDef) {
                insert(questionId, (AgeRangeRuleDef) rule, revisionId);
            } else if (rule instanceof IntRangeRuleDef) {
                insert(questionId, (IntRangeRuleDef) rule, revisionId);
            } else {
                throw new DaoException("Unknown validation rule type " + rule.getRuleType());
            }
        }
    }

    /**
     * Get all validations for a given question by id and language code.
     *
     * @param questionDto the question dto object
     * @param langCodeId the language code id
     * @return list of validations corresponding to the question
     */
    default List<Rule> getValidationRules(QuestionDto questionDto, long langCodeId) {
        List<Rule> rules = new ArrayList<>();

        List<ValidationDto> validationDtos = getJdbiQuestionValidation().getAllActiveValidations(questionDto);

        I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();

        for (ValidationDto validationDto : validationDtos) {
            String correctionHint = null;
            if (validationDto.getHintTemplateId() != null) {
                correctionHint = i18nContentRenderer.renderContent(getHandle(), validationDto.getHintTemplateId(), langCodeId);
            }
            String message = getJdbiI18nValidationMsgTrans().getValidationMessage(
                    getJdbiValidationType().getTypeId(validationDto.getRuleType()),
                    langCodeId
            );
            rules.add(getValidationByIdAndType(validationDto.getId(),
                    validationDto.getRuleType(),
                    validationDto.getAllowSave(),
                    message,
                    correctionHint));
        }

        rules.sort((lhs, rhs) -> {
            if (lhs.getRuleType() == RuleType.REQUIRED) {
                return -1;
            } else if (rhs.getRuleType() == RuleType.REQUIRED) {
                return 1;
            } else {
                return 0;  // Don't change existing order of other rules.
            }
        });

        LOG.info("Found {} validations for question id {} using language code id {}",
                rules.size(), questionDto.getId(), langCodeId);
        return rules;
    }

    /**
     * Get specific validation by its id and type.
     *
     * @param id             the validation primary id
     * @param validationType the validation rule type
     * @param message        the error message for the given validation
     * @param correctionHint the hint for the given validation
     * @return the validation
     */
    default Rule getValidationByIdAndType(long id, RuleType validationType, boolean allowSave,
                                          String message, String correctionHint) {
        switch (validationType) {
            case REQUIRED:
                return new RequiredRule<>(id, correctionHint, message, allowSave);
            case COMPLETE:
                return new CompleteRule<>(id, message, correctionHint, allowSave);
            case LENGTH:
                LengthDto lengthDto = getJdbiLengthValidation()
                        .findById(id)
                        .orElseThrow(() -> new DaoException("Could not find length validation for id: " + id));
                return LengthRule.of(id, message, correctionHint, allowSave, lengthDto.getMinLength(), lengthDto.getMaxLength());
            case REGEX:
                RegexDto regexDto = getJdbiRegexValidation()
                        .findById(id)
                        .orElseThrow(() -> new DaoException("Could not find regex validation for id: " + id));
                return RegexRule.of(id, message, correctionHint, allowSave, regexDto.getRegexPattern());
            case NUM_OPTIONS_SELECTED:
                NumOptionsSelectedDto numOptionsSelectedDto = getJdbiNumOptionsSelectedValidation()
                        .findById(id)
                        .orElseThrow(() -> new DaoException("Could not find num options selected validation for id: " + id));
                return NumOptionsSelectedRule.of(id, message, correctionHint, allowSave,
                        numOptionsSelectedDto.getMinSelections(),
                        numOptionsSelectedDto.getMaxSelections());
            case DAY_REQUIRED:      // fall through
            case MONTH_REQUIRED:    // fall through
            case YEAR_REQUIRED:
                return DateFieldRequiredRule.of(validationType, id, message, correctionHint, allowSave);
            case DATE_RANGE:
                return getJdbiDateRangeValidation().findRuleByIdWithMessage(id, message, correctionHint, allowSave);
            case AGE_RANGE:
                return getJdbiAgeRangeValidation().findRuleByIdWithMessage(id, message, correctionHint, allowSave);
            case INT_RANGE:
                return getJdbiIntRangeValidation().findRuleByIdWithMessage(id, message, correctionHint, allowSave);
            default:
                throw new DaoException("Unknown validation rule type " + validationType);
        }
    }


    /**
     * End currently active validation rules for question by terminating related data.
     *
     * @param questionId the associated question
     * @param meta       the revision metadata used for terminating data
     */
    default void disableValidations(long questionId, RevisionMetadata meta) {
        TemplateDao tmplDao = getTemplateDao();
        JdbiRevision jdbiRev = getJdbiRevision();

        List<ValidationDto> validations = getJdbiQuestionValidation().getAllActiveValidations(questionId);
        if (validations.isEmpty()) {
            LOG.info("No active validations for question id " + questionId);
            return;
        }

        for (ValidationDto dto : validations) {
            if (dto.getHintTemplateId() != null) {
                tmplDao.disableTemplate(dto.getHintTemplateId(), meta);
            }
        }

        List<Long> oldRevIds = validations.stream().map(ValidationDto::getRevisionId).collect(Collectors.toList());
        long[] newRevIds = jdbiRev.bulkCopyAndTerminate(oldRevIds, meta);
        if (newRevIds.length != oldRevIds.size()) {
            throw new DaoException("Not all revisions for validations were terminated");
        }
        int[] numUpdated = getJdbiValidation().bulkUpdateRevisionIdsByDtos(validations, newRevIds);
        if (Arrays.stream(numUpdated).sum() != numUpdated.length) {
            throw new DaoException("Not all validation revisions were updated");
        }

        Set<Long> maybeOrphanedIds = new HashSet<>(oldRevIds);
        for (long revId : maybeOrphanedIds) {
            if (jdbiRev.tryDeleteOrphanedRevision(revId)) {
                LOG.info("Deleted orphaned revision {} by question id {}", revId, questionId);
            }
        }

        LOG.info("Terminated {} validations for question id {}", validations.size(), questionId);
    }

    /**
     * Create basic validation rule by inserting common data. If a hint template is provide, it will be created.
     *
     * <p>Note: this method can be used for rules that does not have rule-specific data, otherwise refer to more specific
     * insertion methods.
     *
     * @param questionId the associated question
     * @param rule       the rule definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertBaseRule(long questionId, RuleDef rule, long revisionId) {
        if (rule.getRuleId() != null) {
            throw new DaoException("Validation id already set to " + rule.getRuleId());
        }

        Long hintTemplateId = null;
        if (rule.getHintTemplate() != null) {
            getTemplateDao().insertTemplate(rule.getHintTemplate(), revisionId);
            hintTemplateId = rule.getHintTemplate().getTemplateId();
        }

        long validationTypeId = getJdbiValidationType().getTypeId(rule.getRuleType());
        long validationId = getJdbiValidation().insert(validationTypeId, rule.getAllowSave(), hintTemplateId, revisionId);
        getJdbiQuestionValidation().insert(questionId, validationId);
        rule.setRuleId(validationId);
    }

    /**
     * End currently active rule by terminating its common data.
     *
     * @param validation the validation rule data
     * @param meta       the revision metadata used for terminating data
     */
    default void disableBaseRule(ValidationDto validation, RevisionMetadata meta) {
        JdbiRevision jdbiRev = getJdbiRevision();

        if (validation.getHintTemplateId() != null) {
            getTemplateDao().disableTemplate(validation.getHintTemplateId(), meta);
        }

        long oldRevId = validation.getRevisionId();
        long newRevId = jdbiRev.copyAndTerminate(oldRevId, meta);
        int numUpdated = getJdbiValidation().updateRevisionIdById(validation.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DaoException("Cannot update revision for validation rule " + validation.getId());
        }

        if (jdbiRev.tryDeleteOrphanedRevision(oldRevId)) {
            LOG.info("Deleted orphaned revision {} by validation rule {}", oldRevId, validation.getId());
        }
    }

    /**
     * Create a required validation rule.
     *
     * @param questionId the associated question
     * @param rule       the rule definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insert(long questionId, RequiredRuleDef rule, long revisionId) {
        insertBaseRule(questionId, rule, revisionId);
    }

    /**
     * Create a completeness validation rule.
     *
     * @param questionId the associated question
     * @param rule       the rule definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insert(long questionId, CompleteRuleDef rule, long revisionId) {
        insertBaseRule(questionId, rule, revisionId);
    }

    /**
     * Create a regex validation rule.
     *
     * @param questionId the associated question
     * @param rule       the rule definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insert(long questionId, RegexRuleDef rule, long revisionId) {
        insertBaseRule(questionId, rule, revisionId);
        getJdbiRegexValidation().insert(rule.getRuleId(), rule.getPattern());
    }

    /**
     * Create a length validation rule.
     *
     * @param questionId the associated question
     * @param rule       the rule definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insert(long questionId, LengthRuleDef rule, long revisionId) {
        insertBaseRule(questionId, rule, revisionId);
        getJdbiLengthValidation().insert(rule.getRuleId(), rule.getMin(), rule.getMax());
    }

    /**
     * Create a number of options selected validation rule.
     *
     * @param questionId the associated question
     * @param rule       the rule definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insert(long questionId, NumOptionsSelectedRuleDef rule, long revisionId) {
        insertBaseRule(questionId, rule, revisionId);
        getJdbiNumOptionsSelectedValidation().insert(rule.getRuleId(), rule.getMin(), rule.getMax());
    }

    /**
     * Create a required date field validation rule.
     *
     * @param questionId the associated question
     * @param rule       the rule definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insert(long questionId, DateFieldRequiredRuleDef rule, long revisionId) {
        insertBaseRule(questionId, rule, revisionId);
    }

    /**
     * Create a date range validation rule.
     *
     * @param questionId the associated question
     * @param rule       the rule definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insert(long questionId, DateRangeRuleDef rule, long revisionId) {
        insertBaseRule(questionId, rule, revisionId);
        getJdbiDateRangeValidation().insert(rule.getRuleId(), rule.getStartDate(), rule.getEndDate(), rule.isUseTodayAsEnd());
    }

    /**
     * Create a age range validation rule.
     *
     * @param questionId the associated question
     * @param rule       the rule definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insert(long questionId, AgeRangeRuleDef rule, long revisionId) {
        insertBaseRule(questionId, rule, revisionId);
        getJdbiAgeRangeValidation().insert(rule);
    }

    default void insert(long questionId, IntRangeRuleDef rule, long revisionId) {
        insertBaseRule(questionId, rule, revisionId);
        getJdbiIntRangeValidation().insert(rule.getRuleId(), rule.getMin(), rule.getMax());
    }

    default Map<Long, List<RuleDef>> collectRuleDefs(Collection<Long> questionIds, long timestamp) {
        Map<Long, List<RuleDef>> questionIdToRuleDefs = new HashMap<>();
        try (var stream = getJdbiQuestionValidation().findDtosByQuestionIdsAndTimestamp(questionIds, timestamp)) {
            stream.forEach(dto -> {
                // todo: query templates, data for validation rules
                RuleDef ruleDef;
                switch (dto.getRuleType()) {
                    case REQUIRED:
                        ruleDef = new RequiredRuleDef(null);
                        break;
                    case COMPLETE:
                        ruleDef = new CompleteRuleDef(null);
                        break;
                    case LENGTH:
                        ruleDef = new LengthRuleDef(null, null, null);
                        break;
                    case REGEX:
                        ruleDef = new RegexRuleDef(null, "");
                        break;
                    case NUM_OPTIONS_SELECTED:
                        ruleDef = new NumOptionsSelectedRuleDef(null, null, null);
                        break;
                    case DAY_REQUIRED:      // fall through
                    case MONTH_REQUIRED:    // fall through
                    case YEAR_REQUIRED:
                        ruleDef = new DateFieldRequiredRuleDef(dto.getRuleType(), null);
                        break;
                    case AGE_RANGE:
                        ruleDef = new AgeRangeRuleDef(null, 0, null);
                        break;
                    case DATE_RANGE:
                        ruleDef = new DateRangeRuleDef(null, null, null, false);
                        break;
                    case INT_RANGE:
                        ruleDef = new IntRangeRuleDef(null, null, null);
                        break;
                    default:
                        throw new DaoException("Unhandled validation rule type " + dto.getRuleType());
                }
                ruleDef.setHintTemplateId(dto.getHintTemplateId());
                questionIdToRuleDefs
                        .computeIfAbsent(dto.getQuestionId(), id -> new ArrayList<>())
                        .add(ruleDef);
            });
        }
        return questionIdToRuleDefs;
    }
}
