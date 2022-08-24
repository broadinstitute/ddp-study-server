package org.broadinstitute.dsm.model.elastic.filter;

import java.util.Objects;

import org.broadinstitute.dsm.model.participant.Util;

public class FilterSeparatorFactory {

    public static AndOrFilterSeparator create(String alias, String filter) {
        AndOrFilterSeparator andOrFilterSeparator;
        if (Util.isUnderDsmKey(Objects.requireNonNull(alias))) {
            andOrFilterSeparator = new AndOrFilterSeparator(Objects.requireNonNull(filter));
        } else {
            andOrFilterSeparator = new NonDsmAndOrFilterSeparator(Objects.requireNonNull(filter));
        }
        return andOrFilterSeparator;
    }

}
