package org.broadinstitute.dsm.util;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.util.Date;

import lombok.NonNull;
import org.broadinstitute.dsm.exception.DsmInternalError;

public class DateTimeUtil {

    public static int calculateAgeInYears(@NonNull String dateOfBirth) {
        LocalDate dob = LocalDate.parse(dateOfBirth);
        LocalDate curDate = LocalDate.now();
        if ((dob != null) && (curDate != null)) {
            return Period.between(dob, curDate).getYears();
        } else {
            throw new DsmInternalError("Could not calculate age for dateOfBirth " + dateOfBirth);
        }
    }

    public static boolean isAdult(@NonNull String dateOfMajority) {
        LocalDate dom = LocalDate.parse(dateOfMajority);
        LocalDate curDate = LocalDate.now();
        return curDate.isAfter(dom) || curDate.isEqual(dom);
    }

    public static String getDateFromEpoch(long epoch) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
        return sdf.format(new Date(epoch));
    }
}
