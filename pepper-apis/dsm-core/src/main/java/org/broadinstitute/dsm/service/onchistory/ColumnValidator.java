package org.broadinstitute.dsm.service.onchistory;

import static org.broadinstitute.dsm.util.SystemUtil.DATE_FORMAT;
import static org.broadinstitute.dsm.util.SystemUtil.DDP_DATE_FORMAT;
import static org.broadinstitute.dsm.util.SystemUtil.INTL_DATE_FORMAT;
import static org.broadinstitute.dsm.util.SystemUtil.US_DATE_FORMAT;

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

    private static List<String> dateFormats = List.of(US_DATE_FORMAT, DDP_DATE_FORMAT, INTL_DATE_FORMAT);

    /**
     * Constructor
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
     * @return ColumnValidatorResponse
     */
    public ColumnValidatorResponse validate(String value, String columnName, String validationType) {
        ColumnValidatorResponse res = new ColumnValidatorResponse();
        switch (validationType) {
            case "s":
                return res;
            case "d":
                try {
                    getDateString(value, DATE_FORMAT);
                } catch (ParseException e) {
                    for (String dateFormat: dateFormats) {
                        try {
                            res.newValue = getDateString(value, dateFormat);
                            return res;
                        } catch (ParseException pe) {
                            continue;
                        }
                    }
                    res.errorMessage = String.format("Invalid date format for column %s: %s", columnName, value);
                    res.valid = false;
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

    private String getDateString(String value, String pattern) throws ParseException {
        SimpleDateFormat parser = new SimpleDateFormat(pattern);
        parser.setLenient(false);
        Date date = parser.parse(value);
        parser.applyPattern(DATE_FORMAT);
        return parser.format(date);
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
