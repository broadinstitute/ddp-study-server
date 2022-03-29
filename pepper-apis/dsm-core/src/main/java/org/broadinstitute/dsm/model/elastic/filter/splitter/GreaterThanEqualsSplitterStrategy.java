package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.Filter;

public class GreaterThanEqualsSplitterStrategy extends SplitterStrategy {

    @Override
    public String[] split() {
        return filter.split(Filter.LARGER_EQUALS_TRIMMED);
    }
}
