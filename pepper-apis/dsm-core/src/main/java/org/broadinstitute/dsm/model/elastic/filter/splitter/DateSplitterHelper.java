package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.Util;

class DateSplitterHelper {
    public static String[] splitter(String filter) {
        return new String[] {filter.split(Filter.DATE_FORMAT)[1].replace(Filter.OPEN_PARENTHESIS, StringUtils.EMPTY)
                .replace(Filter.CLOSE_PARENTHESIS, StringUtils.EMPTY).split(Util.COMMA_SEPARATOR)[0]};
    }
}
