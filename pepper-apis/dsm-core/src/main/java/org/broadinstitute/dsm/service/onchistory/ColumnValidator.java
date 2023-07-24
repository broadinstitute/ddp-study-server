package org.broadinstitute.dsm.service.onchistory;

import static org.broadinstitute.dsm.util.SystemUtil.DATE_FORMAT;
import static org.broadinstitute.dsm.util.SystemUtil.DDP_DATE_FORMAT;
import static org.broadinstitute.dsm.util.SystemUtil.INTL_DATE_FORMAT;
import static org.broadinstitute.dsm.util.SystemUtil.PARTIAL_DATE_FORMAT;
import static org.broadinstitute.dsm.util.SystemUtil.PARTIAL_US_DATE_FORMAT;
import static org.broadinstitute.dsm.util.SystemUtil.US_DATE_FORMAT;
import static org.broadinstitute.dsm.util.SystemUtil.YEAR_DATE_FORMAT;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.FieldSettings;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DsmInternalError;

@Slf4j
public class ColumnValidator {

    private final Map<String, List<String>> columnToPickList;

    private static final List<String> dateFormats = List.of(DATE_FORMAT, US_DATE_FORMAT, DDP_DATE_FORMAT, INTL_DATE_FORMAT);

    private static final List<String> partialDateFormats = List.of(PARTIAL_US_DATE_FORMAT, PARTIAL_DATE_FORMAT);

    /**
     * Constructor
     * @param columnToValues map of column name to vocabulary possible values
     */
    ColumnValidator(Map<String, List<String>> columnToValues) {
        columnToPickList = columnToValues;
    }

    /**
     * Validate a column value
     *
     * @param validationType validation type code (s:string, d:date, i:integer, o:options)
     * @return ColumnValidatorResponse
     */
    public ColumnValidatorResponse validate(String value, String columnName, String validationType) {
        ColumnValidatorResponse res = new ColumnValidatorResponse();
        switch (validationType) {
            case "s":
                return res;
            case "d":
                String formattedValue = parseDate(value, dateFormats, DATE_FORMAT);
                if (formattedValue == null) {
                    formattedValue = parseDate(value, partialDateFormats, PARTIAL_DATE_FORMAT);
                    if (formattedValue == null) {
                        formattedValue = getDateString(value, YEAR_DATE_FORMAT, YEAR_DATE_FORMAT);
                    }
                }
                if (formattedValue == null) {
                    res.errorMessage = String.format("Invalid date format for column %s: %s", columnName, value);
                    res.valid = false;
                } else {
                    res.newValue = formattedValue;
                }
                return res;
            case "i":
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    res.errorMessage = String.format("Invalid number for column %s: %s", columnName, value);
                    res.valid = false;
                }
                return res;
            case "o":
                return validatePickListValue(value, columnName, res);
            default:
                // assertion
                throw new DsmInternalError("Invalid column validation type: " + validationType);
        }
    }

    private String parseDate(String dateValue, List<String> dateFormats, String applyFormat) {
        for (String dateFormat: dateFormats) {
            String formattedValue = getDateString(dateValue, dateFormat, applyFormat);
            if (formattedValue != null) {
                return formattedValue;
            }
        }
        return null;
    }

    private String getDateString(String value, String pattern, String applyFormat) {
        SimpleDateFormat parser = new SimpleDateFormat(pattern);
        parser.setLenient(false);
        try {
            Date date = parser.parse(value);
            parser.applyPattern(applyFormat);
            return parser.format(date);
        } catch (ParseException e) {
            return null;
        }
    }

    private ColumnValidatorResponse validatePickListValue(String value, String columnName, ColumnValidatorResponse res) {
        List<String> values = columnToPickList.get(columnName);
        if (values == null) {
            // assertion
            throw new DsmInternalError("Possible values not found for column " + columnName);
        }

        if (!values.contains(value)) {
            res.errorMessage = String.format("Invalid value for column %s: %s. Valid values are: %s",
                    columnName, value, values);
            res.valid = false;
        }
        return res;
    }
}
