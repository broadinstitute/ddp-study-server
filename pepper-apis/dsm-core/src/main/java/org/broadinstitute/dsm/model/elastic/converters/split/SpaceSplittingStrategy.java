
package org.broadinstitute.dsm.model.elastic.converters.split;

import org.broadinstitute.dsm.model.Filter;


/**
 * A production implementation of splitting words with space " " regex pattern
 */
public class SpaceSplittingStrategy implements FieldNameSplittingStrategy {

    @Override
    public String[] split(String words) {
        return words.split(Filter.SPACE);
    }

}

