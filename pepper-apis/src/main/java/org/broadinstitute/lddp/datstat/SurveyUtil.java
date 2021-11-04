package org.broadinstitute.lddp.datstat;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SurveyUtil {

    private static final Logger logger = LoggerFactory.getLogger(SurveyUtil.class);

    public static final String DATE_FORMAT_UI = "MM-dd-yyyy";

    public static void validateDateFormat(@NonNull String format, @NonNull String value) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            Date date = sdf.parse(value);
            if (!value.equals(sdf.format(date))) {
                throw new RuntimeException("Date " + value + " is not in right format ("+ format +").");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Error - parsing value " + value + " to format " + format, ex);
        }
    }

    public static String subStringDOB(String value) {
        if (StringUtils.isNotEmpty(value)) {
            String dayResolutionString = value.substring(0, 10); // removing the Thh:mm:ss
            Date dayResolutionDate = null;
            try {
                dayResolutionDate = new SimpleDateFormat("yyyy-MM-dd").parse(dayResolutionString);
            }
            catch(ParseException e) {
                throw new RuntimeException("Unable to parse " + value + " into a date",e);
            }
            return new SimpleDateFormat(DATE_FORMAT_UI).format(dayResolutionDate);
        }
        return value;
    }
}