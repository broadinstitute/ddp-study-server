package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.Util;

public class DateGreaterSplitter extends GreaterThanEqualsSplitter {

    @Override
    public String[] getValue() {
        return DateSplitterHelper.splitter(splittedFilter[1]);
    }
}
