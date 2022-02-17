package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.Util;

public class StrDateSplitter extends BaseSplitter {

    @Override
    public String[] split() {
        // STR_TO_DATE(m.fax_sent,'%Y-%m-%d') = STR_TO_DATE('2021-12-17','%Y-%m-%d')
        String[] splittedFilter = new String[2];
        String[] dateFieldWithValue = filter.split(Filter.EQUALS_TRIMMED);
        for (int i = 0; i < dateFieldWithValue.length; i++) {
            splittedFilter[i] = dateFieldWithValue[i].split(Filter.DATE_FORMAT)[1]
                    .replace(Filter.OPEN_PARENTHESIS, StringUtils.EMPTY)
                    .replace(Filter.CLOSE_PARENTHESIS, StringUtils.EMPTY)
                    .split(Util.COMMA_SEPARATOR)[0];
        }
        return splittedFilter;
    }
}
