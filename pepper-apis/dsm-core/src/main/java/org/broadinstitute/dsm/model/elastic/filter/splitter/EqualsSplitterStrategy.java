package org.broadinstitute.dsm.model.elastic.filter.splitter;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;

public class EqualsSplitterStrategy extends SplitterStrategy {

    @Override
    public String[] split() {
        String[] splittedFilter = filter.split(Filter.EQUALS_TRIMMED);
        return Arrays.stream(splittedFilter)
                .map(str -> str.replaceAll("( )+", " "))
                .collect(Collectors.toList())
                .toArray(new String[] {});
    }
}
