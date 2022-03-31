package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.Filter;

public class LessThanEqualsSplitterStrategy extends SplitterStrategy {

    @Override
    public String[] split() {
        return filter.split(Filter.SMALLER_EQUALS_TRIMMED);
    }
}
