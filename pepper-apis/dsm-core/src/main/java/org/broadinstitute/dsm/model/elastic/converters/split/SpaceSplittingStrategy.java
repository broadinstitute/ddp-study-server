
package org.broadinstitute.dsm.model.elastic.converters.split;

import org.broadinstitute.dsm.model.Filter;

public class SpaceSplittingStrategy implements FieldNameSplittingStrategy {

    @Override
    public String[] split(String words) {
        return words.split(Filter.SPACE);
    }

}

