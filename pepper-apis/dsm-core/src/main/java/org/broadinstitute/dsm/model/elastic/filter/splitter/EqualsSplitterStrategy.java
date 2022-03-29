package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.Filter;

public class EqualsSplitterStrategy extends SplitterStrategy {

    @Override
    public String[] split() {
        return filter.split(Filter.EQUALS_TRIMMED);
    }
}
