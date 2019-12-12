package org.broadinstitute.ddp.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile.SqlQuery;
import org.broadinstitute.ddp.constants.SqlConstants.LengthValidationTable;
import org.broadinstitute.ddp.constants.SqlConstants.NumOptionsSelectedValidationTable;
import org.broadinstitute.ddp.constants.SqlConstants.RegexValidationTable;
import org.broadinstitute.ddp.constants.SqlConstants.ValidationTable;
import org.broadinstitute.ddp.constants.SqlConstants.ValidationTypeTable;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.dao.JdbiAgeRangeValidation;
import org.broadinstitute.ddp.db.dao.JdbiDateRangeValidation;
import org.broadinstitute.ddp.db.dao.JdbiIntRangeValidation;
import org.broadinstitute.ddp.model.activity.instance.validation.CompleteRule;
import org.broadinstitute.ddp.model.activity.instance.validation.DateFieldRequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.LengthRule;
import org.broadinstitute.ddp.model.activity.instance.validation.NumOptionsSelectedRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RegexRule;
import org.broadinstitute.ddp.model.activity.instance.validation.RequiredRule;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidationDao {

    private static final Logger LOG = LoggerFactory.getLogger(ValidationDao.class);

    private String validationsForQuestionQuery;
    private String minAndMaxLengthValidationQuery;
    private String regexPatternValidationQuery;
    private String numOptionsSelectedValidationQuery;
    private I18nContentRenderer i18nContentRenderer;

    /**
     * Set up needed sequel queries.
     */
    public ValidationDao(String validationsForQuestionQuery,
                         String minAndMaxLengthValidationQuery,
                         String regexPatternValidationQuery,
                         String numOptionsSelectedValidationQuery,
                         I18nContentRenderer i18nContentRenderer) {
        this.validationsForQuestionQuery = validationsForQuestionQuery;
        this.minAndMaxLengthValidationQuery = minAndMaxLengthValidationQuery;
        this.regexPatternValidationQuery = regexPatternValidationQuery;
        this.numOptionsSelectedValidationQuery = numOptionsSelectedValidationQuery;
        this.i18nContentRenderer = i18nContentRenderer;
    }

    /**
     * Build an validation dao using sql from given config.
     *
     * @param sqlConfig the config with sql queries and statements
     * @return validation dao object
     */
    public static ValidationDao fromSqlConfig(Config sqlConfig) {
        return new ValidationDao(
                sqlConfig.getString(SqlQuery.VALIDATIONS_FOR_QUESTION),
                sqlConfig.getString(SqlQuery.MIN_AND_MAX_LENGTH_VALIDATION),
                sqlConfig.getString(SqlQuery.REGEX_PATTERN_VALIDATION),
                sqlConfig.getString(SqlQuery.NUM_OPTIONS_SELECTED_VALIDATION),
                new I18nContentRenderer()
        );
    }

    /**
     * Get all validations for a given question translated to given language code.
     *
     * @param handle         the jdbi handle
     * @param questionId     the question primary id
     * @param languageCodeId the language code primary id
     * @return list of validations
     */
    public List<Rule> getActiveValidations(Handle handle, String instanceGuid, long questionId, long languageCodeId) {
        List<Long> validationIds = new ArrayList<>();
        List<RuleType> validationTypes = new ArrayList<>();
        List<String> defaultMessages = new ArrayList<>();
        List<String> correctionHints = new ArrayList<>();
        List<Boolean> allowSaves = new ArrayList<>();

        try (PreparedStatement stmt = handle.getConnection().prepareStatement(validationsForQuestionQuery)) {
            stmt.setString(1, instanceGuid);
            stmt.setLong(2, questionId);
            stmt.setLong(3, languageCodeId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                validationIds.add(rs.getLong(ValidationTable.ID));
                allowSaves.add(rs.getBoolean(ValidationTable.ALLOW_SAVE));
                validationTypes.add(RuleType.valueOf(rs.getString(ValidationTypeTable.TYPE_CODE)));
                defaultMessages.add(rs.getString(ValidationTable.TRANSLATION_TEXT));
                Long correctionHintTemplateId = (Long) rs.getObject(ValidationTable.CORRECTION_HINT);
                if (correctionHintTemplateId == null) {
                    correctionHints.add(null);
                } else {
                    String correctionHint = i18nContentRenderer.renderContent(handle, correctionHintTemplateId,
                            languageCodeId);
                    correctionHints.add(correctionHint);
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Could not find validations for question id "
                    + questionId + " using language code id " + languageCodeId, e);
        }

        List<Rule> validations = new ArrayList<>();
        for (int i = 0; i < validationIds.size(); i++) {
            validations.add(getValidationByIdAndType(handle, validationIds.get(i),
                    validationTypes.get(i), allowSaves.get(i), defaultMessages.get(i), correctionHints.get(i)));
        }

        validations.sort((lhs, rhs) -> {
            if (lhs.getRuleType() == RuleType.REQUIRED) {
                return -1;
            } else if (rhs.getRuleType() == RuleType.REQUIRED) {
                return 1;
            } else {
                return 0;  // Don't change existing order of other rules.
            }
        });

        LOG.info("Found {} validations for question id {} using language code id {}",
                validations.size(), questionId, languageCodeId);
        return validations;
    }

    /**
     * Get specific validation by its id and type.
     *
     * @param handle         the jdbi handle
     * @param id             the validation primary id
     * @param validationType the validation rule type
     * @return the validation
     */
    private Rule getValidationByIdAndType(Handle handle, long id, RuleType validationType, boolean allowSave,
                                          String message, String correctionHint) {
        switch (validationType) {
            case REQUIRED:
                return new RequiredRule<>(id, correctionHint, message, allowSave);
            case COMPLETE:
                return new CompleteRule<>(id,  message, correctionHint, allowSave);
            case LENGTH:
                return getLengthValidationById(handle, id, message, allowSave, correctionHint);
            case REGEX:
                return getRegexValidationById(handle, id, message, allowSave, correctionHint);
            case NUM_OPTIONS_SELECTED:
                return getNumOptionsSelectedValidationById(handle, id, message, allowSave, correctionHint);
            case DAY_REQUIRED:      // fall through
            case MONTH_REQUIRED:    // fall through
            case YEAR_REQUIRED:
                return DateFieldRequiredRule.of(validationType, id, message, correctionHint, allowSave);
            case DATE_RANGE:
                return handle.attach(JdbiDateRangeValidation.class).findRuleByIdWithMessage(id, message, correctionHint, allowSave);
            case AGE_RANGE:
                return handle.attach(JdbiAgeRangeValidation.class).findRuleByIdWithMessage(id, message, correctionHint, allowSave);
            case INT_RANGE:
                return handle.attach(JdbiIntRangeValidation.class).findRuleByIdWithMessage(id, message, correctionHint, allowSave);
            default:
                throw new DaoException("Unknown validation rule type " + validationType);
        }
    }

    /**
     * Create a length validation object based on validation id and given error message.
     *
     * @param handle  the jdbi handle
     * @param id      the validation primary id
     * @param message the error message for the given validation
     * @return length validation object
     */
    private LengthRule getLengthValidationById(Handle handle, Long id, String message, boolean allowSave, String correctionHint) {
        Integer min = null;
        Integer max = null;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(minAndMaxLengthValidationQuery)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                min = (Integer) rs.getObject(LengthValidationTable.MIN_LENGTH);
                max = (Integer) rs.getObject(LengthValidationTable.MAX_LENGTH);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not retrieve min/ max length for validation " + id, e);
        }
        return LengthRule.of(id, message, correctionHint, allowSave, min, max);
    }

    /**
     * Create a regex validation object based on validation id and given error message.
     *
     * @param handle  the jdbi handle
     * @param id      the validation primary id
     * @param message the error message for the given validation
     * @return regex validation object
     */
    private RegexRule getRegexValidationById(Handle handle, Long id, String message, boolean allowSave, String correctionHint) {
        String regexPattern = null;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(regexPatternValidationQuery)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                regexPattern = rs.getString(RegexValidationTable.REGEX_PATTERN);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not retrieve regex pattern for validation " + id, e);
        }
        return RegexRule.of(id, message, correctionHint, allowSave, regexPattern);
    }

    /**
     * Create a validation object based on validation id and given error message.
     *
     * @param handle  the jdbi handle
     * @param id      the validation primary id
     * @param message the error message for the given validation
     * @return validation object
     */
    private NumOptionsSelectedRule getNumOptionsSelectedValidationById(Handle handle, Long id, String message,
                                                                       boolean allowSave, String correctionHint) {
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(numOptionsSelectedValidationQuery)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Integer minSelections = (Integer) rs.getObject(NumOptionsSelectedValidationTable.MIN_SELECTIONS);
                Integer maxSelections = (Integer) rs.getObject(NumOptionsSelectedValidationTable.MAX_SELECTIONS);
                return NumOptionsSelectedRule.of(id, message, correctionHint, allowSave, minSelections, maxSelections);
            }
            throw new NoSuchElementException("Validation with id " + id + " was not found in the database");
        } catch (SQLException e) {
            throw new RuntimeException("Could not retrieve min/max selections info for validation " + id, e);
        }
    }
}
