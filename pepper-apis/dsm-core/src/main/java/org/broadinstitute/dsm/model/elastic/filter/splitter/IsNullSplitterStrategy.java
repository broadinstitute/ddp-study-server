package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.Filter;

public class IsNullSplitterStrategy extends NullBaseSplitterStrategy {

    @Override
    public String[] split() {
        return filter.split(Filter.IS_NULL_TRIMMED);
    }
}
