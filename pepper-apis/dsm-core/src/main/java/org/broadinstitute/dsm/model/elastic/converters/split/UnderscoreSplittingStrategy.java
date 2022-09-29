
package org.broadinstitute.dsm.model.elastic.converters.split;

import static org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter.UNDERSCORE_SEPARATOR;

/**
 * A production implementation for splitting words with the underscore "_" regex pattern
 */
public class UnderscoreSplittingStrategy implements FieldNameSplittingStrategy {

    @Override
    public String[] split(String words) {
        return words.split(UNDERSCORE_SEPARATOR);
    }

}

