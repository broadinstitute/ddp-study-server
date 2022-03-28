package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.Filter;

public class IsNotNullSplitterStrategy extends NullBaseSplitter {

    @Override
    public String[] split() {
        return filter.split(Filter.IS_NOT_NULL_TRIMMED);
    }
}
