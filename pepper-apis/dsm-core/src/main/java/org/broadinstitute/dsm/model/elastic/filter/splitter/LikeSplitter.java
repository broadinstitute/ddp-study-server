package org.broadinstitute.dsm.model.elastic.filter.splitter;


import java.util.Arrays;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;

public class LikeSplitter extends BaseSplitter {

    @Override
    public String[] split() {
        String[] splittedFilter = filter.split(Filter.LIKE_TRIMMED);
        return Arrays.stream(splittedFilter)
                .map(str -> str.replace("%", ""))
                .collect(Collectors.toList())
                .toArray(new String[] {});
    }
}
