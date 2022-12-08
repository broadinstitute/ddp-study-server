package org.broadinstitute.dsm.model.elastic.filter.splitter;


import java.util.Arrays;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;

public class LikeSplitterStrategy extends SplitterStrategy {

    @Override
    public String[] split() {
        String[] splittedFilter = filter.split(Filter.LIKE_TRIMMED);
        return Arrays.stream(splittedFilter)
                .map(str -> str.replace("%", "").replaceAll("( )+", " "))
                .collect(Collectors.toList())
                .toArray(new String[] {});
    }
}
