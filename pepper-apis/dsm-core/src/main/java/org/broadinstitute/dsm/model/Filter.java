package org.broadinstitute.dsm.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.model.filter.participant.BaseFilterParticipantList;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Filter {

    public static final Logger logger = LoggerFactory.getLogger(Filter.class);

    public static final String EQUALS = " = ";
    public static final String EQUALS_TRIMMED = "=";
    public static final String LARGER_EQUALS = " >= ";
    public static final String LARGER_EQUALS_TRIMMED = ">=";
    public static final String SMALLER_EQUALS = " <= ";
    public static final String SMALLER_EQUALS_TRIMMED = "<=";
    public static final String AND = " AND ";
    public static final String AND_TRIMMED = "AND";
    public static final String IS_NOT_NULL = " IS NOT NULL";
    public static final String IS_NULL = " IS NULL";
    public static final String IS = "IS";
    public static final String NOT = "NOT";
    public static final String NULL = "NULL";
    public static final String LIKE = " LIKE ";
    public static final String LIKE_TRIMMED = "LIKE";
    public static final String OR = " OR ";
    public static final String OR_TRIMMED = "OR";
    public static final String TRUE = "true";
    public static final String FALSE = "false";
    public static final String TODAY = "today";
    public static final String SINGLE_QUOTE = "'";
    public static final String DB_NAME_DELIMITER = ".";
    public static final String PLUS_SIGN = "+";
    public static final String MINUS_SIGN = "-";
    public static final String PERCENT_SIGN = "%";
    public static final String DAYS_SIGN = "d";
    public static final String SPACE = " ";
    public static final String OPEN_PARENTHESIS = "(";
    public static final String CLOSE_PARENTHESIS = ")";
    public static final String JSON_EXTRACT = "JSON_EXTRACT";
    public static final String JSON_CONTAINS = "JSON_CONTAINS";
    public static final String JSON_OBJECT = "JSON_OBJECT";
    public static final int THOUSAND = 1000;
    public static final String IS_NOT_NULL_TRIMMED = "IS NOT NULL";
    public static final String DIAMOND_EQUALS = "<=>";
    public static final String DATE_FORMAT = "STR_TO_DATE";
    public static final String DATE_GREATER = LARGER_EQUALS + DATE_FORMAT;
    public static final String DATE_LESS = SMALLER_EQUALS + DATE_FORMAT;
    public static final char OPEN_PARENTHESIS_CHAR = '(';
    public static final char CLOSE_PARENTHESIS_CHAR = ')';
    public static final String IS_NULL_TRIMMED = "IS NULL";

    public static String TEXT = "TEXT";
    public static String OPTIONS = "OPTIONS";
    public static String DATE = "DATE";
    public static String DATE_SHORT = "DATE_SHORT";
    public static String ADDITIONAL_VALUES = "ADDITIONALVALUE";
    public static String NUMBER = "NUMBER";
    public static String BOOLEAN = "BOOLEAN";
    public static String CHECKBOX = "CHECKBOX";
    public static String COMPOSITE = "COMPOSITE";//ES type
    public static String JSON_ARRAY = "JSONARRAY";//Sample result
    public static String AGREEMENT = "AGREEMENT";


    private boolean range = false;
    private boolean exactMatch = false;
    private boolean empty = false;
    private boolean notEmpty = false;
    public String type;
    private String parentName;
    private NameValue filter1;
    private NameValue filter2;
    private String[] selectedOptions;
    private ParticipantColumn participantColumn;
    private String additionalType;

    public Filter() {
    }

    public Filter(boolean range, boolean exactMatch, boolean empty, boolean notEmpty,
                  String type, String parentName, NameValue filter1, NameValue filter2,
                  String[] selectedOptions, ParticipantColumn participantColumn) {
        this.setRange(range);
        this.setExactMatch(exactMatch);
        this.setEmpty(empty);
        this.setNotEmpty(notEmpty);
        this.setType(type);
        this.setParentName(parentName);
        this.setFilter1(filter1);
        this.setFilter2(filter2);
        this.setSelectedOptions(selectedOptions);
        this.setParticipantColumn(participantColumn);
    }

    public static String getQueryStringForFiltering(@NonNull Filter filter, DBElement dbElement) {
        String finalQuery = "";
        String query = "";
        String condition = "";
        //simple is better than complex, KISS(Keep It Simple Stupid)
        if (filter.isEmpty() && !ADDITIONAL_VALUES.equals(filter.getType()) && !JSON_ARRAY.equals(filter.getType())) {
            finalQuery = AND + filter.getColumnName(dbElement) + IS_NULL + " ";
        }
        else if (filter.isNotEmpty() && !ADDITIONAL_VALUES.equals(filter.getType()) && !JSON_ARRAY.equals(filter.getType())) {
            finalQuery = AND + filter.getColumnName(dbElement) + IS_NOT_NULL + " ";
        }

        if ((StringUtils.isBlank(filter.getType()) || TEXT.equals(filter.getType()) || COMPOSITE.equals(filter.getType())) && (filter.getFilter1() != null) && filter.getFilter1().getValue() != null && (StringUtils.isNotBlank(String.valueOf(filter.getFilter1().getValue())))) {
            filter.getFilter1().setValue(replaceQuotes(filter.getFilter1().getValue()));
            if (filter.isExactMatch()) {
                condition = EQUALS + "'" + filter.getFilter1().getValue() + "'";
            }
            else {
                condition = " " + LIKE + " \'%" + filter.getFilter1().getValue() + "%\'";
            }
            query = AND + filter.getColumnName(dbElement);
            finalQuery = query + condition;
        }
        else if (NUMBER.equals(filter.getType()) && !filter.empty) {
            if (!filter.isRange()) {
                if (filter.getFilter1() != null && StringUtils.isNotBlank(String.valueOf(filter.getFilter1().getValue())) && filter.getFilter1().getValue() != null) {
                    query = AND + filter.getColumnName(dbElement);
                    condition = EQUALS + filter.getFilter1().getValue();
                    finalQuery = query + condition;
                }
            }
            else {
                String notNullQuery = AND + filter.getColumnName(dbElement) + IS_NOT_NULL;
                if (filter.getFilter1() != null && filter.getFilter1().getValue() != null && StringUtils.isNotBlank(String.valueOf(filter.getFilter1().getValue()))) {
                    query = AND + filter.getColumnName(dbElement);
                    condition = LARGER_EQUALS + (int) Double.parseDouble(String.valueOf(filter.getFilter1().getValue()));
                }
                String query2 = "";
                String condition2 = "";
                if (filter.getFilter2() != null && filter.getFilter2() != null && filter.getFilter2().getValue() != null && StringUtils.isNotBlank(String.valueOf(filter.getFilter2().getValue()))) {
                    query2 = AND + filter.getColumnName(dbElement);
                    condition2 = SMALLER_EQUALS + (int) Double.parseDouble(String.valueOf(filter.getFilter2().getValue()));
                }
                finalQuery = query + condition + query2 + condition2 + notNullQuery;
                if (isNotEmpty(filter.getFilter1()) || isNotEmpty(filter.getFilter2())) {
                    finalQuery = finalQuery + notNullQuery;
                }
            }
        }
        else if (OPTIONS.equals(filter.getType())) {
            if (filter.getSelectedOptions().length < 1) {
                return finalQuery;
            }
            finalQuery = AND + "( ";
            for (String selectedOption : filter.getSelectedOptions()) {
                query = filter.getColumnName(dbElement);
                condition = EQUALS + "'" + selectedOption + "'";
                finalQuery = finalQuery + query + condition + OR;
            }
            finalQuery = finalQuery.substring(0, finalQuery.length() - 4);
            finalQuery += " ) ";
        }
        else if (DATE.equals(filter.getType()) || DATE_SHORT.equals(filter.getType())) {
            if (!filter.isRange()) {
                if (filter.getFilter1() != null) {
                    query = AND + filter.getColumnName(dbElement);
                    if (String.valueOf(filter.getFilter1().getValue()).length() == 10) {
                        finalQuery = generateDateComparisonSql(filter, dbElement,EQUALS,filter.getFilter1().getValue(), false);
                    }
                    else {
                        if (filter.isEmpty()) {
                            finalQuery = query + IS_NULL + " ";
                        }
                        else if (filter.isNotEmpty()) {
                            finalQuery = query + IS_NOT_NULL + " ";
                        }
                        else {
                            throw new RuntimeException("Cannot compare to unknown date format " + filter.getFilter1().getValue());
                        }
                    }
                }
            }
            else {
                filter = convertFilterDateValues(filter);
                String query1 = "";
                if (filter.getFilter1() != null && filter.getFilter1().getValue() != null && StringUtils.isNotBlank(String.valueOf(filter.getFilter1().getValue()))) {
                    query1 = generateDateComparisonSql(filter,dbElement, LARGER_EQUALS, filter.getFilter1().getValue(), false);
                }
                String query2 = "";
                if (filter.getFilter2() != null && filter.getFilter2() != null && filter.getFilter2().getValue() != null && StringUtils.isNotBlank(String.valueOf(filter.getFilter2().getValue()))) {
                    query2 = generateDateComparisonSql(filter,dbElement, SMALLER_EQUALS,filter.getFilter2().getValue(), true);
                }
                finalQuery = query1 + query2;
            }
        }
        else if (ADDITIONAL_VALUES.equals(filter.getType())) {
            finalQuery = buildJsonExtract(filter, dbElement);
        }
        else if (JSON_ARRAY.equals(filter.getType())) {
            query = AND + filter.getParentName() + DBConstants.ALIAS_DELIMITER + dbElement.getColumnName() + DBConstants.ALIAS_DELIMITER + filter.getFilter2().getName();
            if (filter.isEmpty()) {
                finalQuery = query + IS_NULL + " ";
            }
            else if (filter.isNotEmpty()) {
                finalQuery = query + IS_NOT_NULL + " ";
            } else {
                if (filter.getFilter1() != null && filter.getFilter1().getValue() != null && StringUtils.isNotBlank(String.valueOf(filter.getFilter1().getValue()))) {
                    String quotation = "'";
                    if (filter.isExactMatch()) {
                        query = AND + "JSON_CONTAINS ( " + filter.getParentName() + DBConstants.ALIAS_DELIMITER + dbElement.getColumnName() + " , JSON_OBJECT ( '" + filter.getFilter2().getName() + "' , "+quotation + filter.getFilter1().getValue() + quotation+" ) ) ";
                    }
                    else {
                        query = AND + filter.getParentName() + DBConstants.ALIAS_DELIMITER + dbElement.getColumnName() + " -> '$[*]." + filter.getFilter2().getName() + "' like '%" + filter.getFilter1().getValue() + "%' ";
                    }
                }
                finalQuery = query;
            }
        }
        else if (CHECKBOX.equals(filter.getType())) {
            if (filter.getFilter1() != null && filter.getFilter1().getValue() != null && StringUtils.isNotBlank(String.valueOf(filter.getFilter1().getValue())) &&
                    (TRUE.equals(filter.getFilter1().getValue()) || TRUE.equals(String.valueOf(filter.getFilter1().getValue())) || "1".equals(filter.getFilter1().getValue()))) {
                query = AND + filter.getColumnName(dbElement) + LIKE + "'1'";
            }
            else if (filter.getFilter2() != null && filter.getFilter2().getValue() != null && StringUtils.isNotBlank(String.valueOf(filter.getFilter2().getValue())) &&
                    (TRUE.equals(filter.getFilter2().getValue()) || TRUE.equals(String.valueOf(filter.getFilter2().getValue())) || "1".equals(filter.getFilter2().getValue()))){
                query = AND + NOT + " " + filter.getColumnName(dbElement) + " <=> 1";
            }
            finalQuery = query;
        }
        else if (BOOLEAN.equals(filter.getType()) || AGREEMENT.equals(filter.getType())) { //true/false
            if (filter.getFilter1() != null && filter.getFilter1().getValue() != null && StringUtils.isNotBlank(String.valueOf(filter.getFilter1().getValue())) && TRUE.equals(filter.getFilter1().getValue())) {
                query = AND + filter.getParentName() + DBConstants.ALIAS_DELIMITER + filter.getFilter1().getName() + EQUALS + filter.getFilter1().getValue();
            }
            else if (filter.getFilter1() != null && filter.getFilter2().getValue() != null && StringUtils.isNotBlank(String.valueOf(filter.getFilter2().getValue())) && TRUE.equals(filter.getFilter2().getValue())) {
                query = AND + filter.getParentName() + DBConstants.ALIAS_DELIMITER + filter.getFilter1().getName() + EQUALS + FALSE;
            }
            finalQuery = query;
        }
        return finalQuery;
    }

    private static String buildJsonExtract(Filter filter, DBElement dbElement) {
        String query;
        String finalQuery;
        String jsonExtract = "JSON_EXTRACT ( ";
        query = AND +
                jsonExtract + filter.getParentName() + DBConstants.ALIAS_DELIMITER + dbElement.getColumnName() + " , '$." + filter.getFilter2().getName() + "' ) ";
        if (filter.isEmpty()) {
            finalQuery = query + IS_NULL + " ";
        }
        else if (filter.isNotEmpty()) {
            finalQuery = query + IS_NOT_NULL + " ";
        }
        else {
            String notNullQuery = AND + filter.getParentName() + DBConstants.ALIAS_DELIMITER + dbElement.getColumnName() + IS_NOT_NULL;
            if (filter.getFilter1() != null && filter.getFilter1().getValue() != null && StringUtils.isNotBlank(String.valueOf(
                    filter.getFilter1().getValue()))) {
                query = AND + jsonExtract + filter.getParentName() + DBConstants.ALIAS_DELIMITER + dbElement.getColumnName() + " , '$." + filter.getFilter2().getName() + "' ) ";
                if (BaseFilterParticipantList.isDateRange(filter)) {
                    String moreThan = generateDateComparisonSql(filter, dbElement, LARGER_EQUALS, filter.getFilter1().getValue(), false);
                    String lessThan = generateDateComparisonSql(filter, dbElement, SMALLER_EQUALS, filter.getFilter2().getValue(), true);
                    int moreThanIndex = moreThan.indexOf(Filter.LARGER_EQUALS_TRIMMED);
                    int lessThanIndex = lessThan.indexOf(Filter.SMALLER_EQUALS_TRIMMED);
                    moreThan = moreThan.substring(moreThanIndex);
                    lessThan = lessThan.substring(lessThanIndex);
                    query += moreThan + query + lessThan;
                } else if (filter.isExactMatch()) {
                    query += NUMBER.equals(filter.additionalType) ? EQUALS + "#" : EQUALS + "'#'";
                    query = query.replaceAll("#", String.valueOf(filter.getFilter1().getValue()));
                } else {
                    query += " " + LIKE + " '%#%'";
                    query = query.replaceAll("#", String.valueOf(filter.getFilter1().getValue()));
                }
            }
            if (Objects.nonNull(filter.getSelectedOptions()) || BaseFilterParticipantList.isDateRange(filter)) {
                finalQuery = query;
            } else {
                finalQuery = notNullQuery + query;
            }
        }
        return finalQuery;
    }

    /**
     * Check if filter value is not null and is not blank
     * @return boolean is true if a filter is not null and not blank
     */
    private static boolean isNotEmpty(NameValue filter) {
        return filter != null && StringUtils.isNotBlank(String.valueOf(filter.getValue()));
    }

    /**
     * Uses the appropriate date converter (if given) to write SQL that can
     * compare either exact dates or "in the day" dates.
     * @param filter
     * @param dbElement
     * @param comparison how the values will be compared to one another
     * @param arg the user-input field to compare
     * @param useEndOfday if false, when parsing a date, the first millis of the day
     *                    will be used.  if true, the last millis of the day will
     *                    be used.
     */
    private static String generateDateComparisonSql(Filter filter, DBElement dbElement, String comparison, Object arg, boolean useEndOfday) {
        String column = filter.getColumnName(dbElement);
        SqlDateConverter dateConverter = null;
        if (dbElement != null) {
            dateConverter = dbElement.getDateConverter();
            Instant instant = null;
            try {
                // 29/07/2021
                LocalDate date = LocalDate.parse(arg.toString(), DateTimeFormatter.ISO_LOCAL_DATE);
                instant = useEndOfday ? date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC) : date.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                // might be an epoch time in an older saved filter
                instant = Instant.ofEpochMilli(Long.parseLong(arg.toString()));
            }

            if (dateConverter != null) {
                if (EQUALS.equals(comparison)) {
                    return AND + dateConverter.convertColumnForSqlDay(column) + " " + comparison + dateConverter.convertArgToSqlDay(instant);
                } else {
                    return AND + column + " " + comparison + dateConverter.convertArgToSql(instant);
                }
            }
        }
        String stringArg = "'" + arg + "'";
        return AND + column + " " + comparison + stringArg;
    }

    private static Filter convertFilterDateValues(Filter filter) {
        if (filter.getFilter1() != null && filter.getFilter1().getValue() != null && String.valueOf(filter.getFilter1().getValue()).length() != 10) {
            if (String.valueOf(filter.getFilter1().getValue()).length() == 4) {
                filter.getFilter1().setValue(filter.getFilter1().getValue() + "-01-01");
            }
            else if (String.valueOf(filter.getFilter1().getValue()).length() == 7) {
                filter.getFilter1().setValue(filter.getFilter1().getValue() + "-01");
            }
        }
        if (filter.getFilter2() != null && filter.getFilter2().getValue() != null && String.valueOf(filter.getFilter2().getValue()).length() != 10) {
            if (String.valueOf(filter.getFilter2().getValue()).length() == 4) {
                filter.getFilter1().setValue(filter.getFilter1().getValue() + "-01-01");
            }
            else if (String.valueOf(filter.getFilter2().getValue()).length() == 7) {
                filter.getFilter1().setValue(filter.getFilter1().getValue() + "-01");
            }
        }
        return filter;
    }

    private String getColumnName(DBElement dbElement) {
        if (dbElement == null) {
            String tmp = StringUtils.isNotBlank(this.getParentName()) ? this.getParentName() : this.getParticipantColumn().getTableAlias();
            if (this.getFilter1() != null && StringUtils.isNotBlank(this.getFilter1().getName())) {
                return tmp + DBConstants.ALIAS_DELIMITER + this.getFilter1().getName();
            }
            else if (this.getFilter2() != null && StringUtils.isNotBlank(this.getFilter2().getName())) {
                return tmp + DBConstants.ALIAS_DELIMITER + this.getFilter2().getName();
            }
        }
        if (StringUtils.isNotBlank(dbElement.getTableAlias())) {
            return dbElement.getTableAlias() + DBConstants.ALIAS_DELIMITER + dbElement.getColumnName();
        }
        else {
            String tmp = StringUtils.isNotBlank(this.getParentName()) ? this.getParentName() : this.getParticipantColumn().getTableAlias();
            return tmp + DBConstants.ALIAS_DELIMITER + dbElement.getColumnName();
        }
    }

    private static Object replaceQuotes(Object text) {
        if (text != null && ((String) text).contains("'")) {
            String tmp = ((String) text).replace("'", "");
            return replaceQuotes(tmp);
        }
        return text;
    }

    public void setRange(boolean range) {
        this.range = range;
    }

    public void setExactMatch(boolean exactMatch) {
        this.exactMatch = exactMatch;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public void setNotEmpty(boolean notEmpty) {
        this.notEmpty = notEmpty;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public void setFilter1(NameValue filter1) {
        this.filter1 = filter1;
    }

    public void setFilter2(NameValue filter2) {
        this.filter2 = filter2;
    }

    public void setSelectedOptions(String[] selectedOptions) {
        this.selectedOptions = selectedOptions;
    }

    public void setParticipantColumn(ParticipantColumn participantColumn) {
        this.participantColumn = participantColumn;
    }

    public void setAdditionalType(String additionalType) {
        this.additionalType = additionalType;
    }
}
