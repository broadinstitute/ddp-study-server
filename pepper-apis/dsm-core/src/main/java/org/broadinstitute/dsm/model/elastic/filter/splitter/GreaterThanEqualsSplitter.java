package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.filter.Operator;

public class GreaterThanEqualsSplitter extends BaseSplitter  {

    @Override
    public String[] split() {
        return filter.split(Filter.LARGER_EQUALS_TRIMMED);
    }
}
