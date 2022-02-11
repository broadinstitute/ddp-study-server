package org.broadinstitute.dsm.util;

import java.util.Map;

/**
 * Contains util methods which could be used to assign ElasticSearch data (when updating ES indices).
 */
public class ElasticSearchDataUtil {

    /**
     * Set the current date as String (using format 'strict_year_month_day' which is the same as 'yyyy-MM-dd')
     * to a specified map element.
     * @param nameValuesMap map where to set a current date
     * @param name  element name where to set a current date
     */
    public static void setCurrentStrictYearMonthDay(Map<String, Object> nameValuesMap, String name) {
        nameValuesMap.put(name, SystemUtil.getStrictYearMonthDay());
    }
}
