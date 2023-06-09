package org.broadinstitute.dsm.service.onchistory;

import static org.broadinstitute.dsm.util.SystemUtil.INTL_DATE_FORMAT;
import static org.broadinstitute.dsm.util.SystemUtil.US_DATE_FORMAT;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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

    /**
     * @param pickLists DTO of field_settings rows that have multiple possible values
     */
    ColumnValidator(List<FieldSettingsDto> pickLists) {
        columnToPickList = pickLists.stream().collect(
                Collectors.toMap(FieldSettingsDto::getColumnName,
                        fs -> FieldSettings.getStringListFromJson(fs.getPossibleValues())));
    }

    /**
     * Validate a column value
     *
     * @param validationType validation type code (s:string, d:date, i:integer, o:options)
     * @param errorMessage buffer for error message on failed validation
     * @return true if column value is valid
     */
    public boolean validate(String value, String columnName, String validationType, StringBuilder errorMessage) {
        switch (validationType) {
            case "s":
                return true;
            case "d":
                try {
                    SimpleDateFormat parser = new SimpleDateFormat(US_DATE_FORMAT);
                    parser.parse(value);
                } catch (ParseException e) {
                    try {
                        SimpleDateFormat parser = new SimpleDateFormat(INTL_DATE_FORMAT);
                        parser.parse(value);
                    } catch (ParseException pe) {
                        errorMessage.append(String.format("Invalid date format for column %s: %s", columnName, value));
                        return false;
                    }
                }
                return true;
            case "i":
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    errorMessage.append(String.format("Invalid number for column %s: %s", columnName, value));
                    return false;
                }
                return true;
            case "o":
                return validatePickListValue(value, columnName, errorMessage);
            default:
                // assertion
                throw new DsmInternalError("Invalid column validation type: " + validationType);
        }
    }

    private boolean validatePickListValue(String value, String columnName, StringBuilder errorMessage) {
        List<String> values = columnToPickList.get(columnName);
        if (values == null) {
            // assertion
            throw new DsmInternalError("Possible values not found for column " + columnName);
        }

        if (!values.contains(value)) {
            errorMessage.append(String.format("Invalid value for column %s: %s. Valid values are: %s",
                    columnName, value, values));
            return false;
        }
        return true;
    }
}
