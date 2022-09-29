
package org.broadinstitute.dsm.model.elastic.converters.split;

/**
 * An abstraction which can be used to inject the strategy of splitting words by some regex separators like space, dot, comma etc..
 */
public interface FieldNameSplittingStrategy {

    /**
     * Returns the array of strings splitted by some regex separator
     * @param words a concatenated set of words as a whole string to be splitted
     */
    String[] split(String words);

}
