package org.broadinstitute.dsm.model.elastic.filter;

import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.broadinstitute.dsm.model.participant.Util;

@AllArgsConstructor
@NoArgsConstructor
@Setter
public class FilterSeparatorFactory {

    private String alias;
    private String filter;

    public AndOrFilterSeparator create() {
        AndOrFilterSeparator andOrFilterSeparator;
        if (Util.isUnderDsmKey(Objects.requireNonNull(alias))) {
            andOrFilterSeparator = new AndOrFilterSeparator(Objects.requireNonNull(filter));
        } else {
            andOrFilterSeparator = new NonDsmAndOrFilterSeparator(Objects.requireNonNull(filter));
        }
        return andOrFilterSeparator;
    }

}
