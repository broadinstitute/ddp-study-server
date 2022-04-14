package org.broadinstitute.ddp.export.collectors;

import java.util.List;
import java.util.Map;

/**
 * Represents a strategy for formatting responses to questions.
 *
 * @param <D> the definition of the question
 * @param <A> the answer object
 */
public interface ResponseFormatStrategy<D, A> {

    /**
     * Determine the mapping data types consumable by Elasticsearch for the question definition.
     *
     * @param definition the definition
     * @return mapping of property names to type objects
     */
    Map<String, Object> mappings(D definition);

    /**
     * Determine the question details consumable by Elasticsearch.
     *
     * @param definition the definition
     * @return mapping of question property details
     */
    Map<String, Object> questionDef(D definition);

    /**
     * Determine the column headers for the question definition.
     *
     * @param definition the definition
     * @return list of column headers
     */
    List<String> headers(D definition);

    /**
     * Format the answer response into an unsorted record of key-value pairs, where the key is the column header and the value is the value
     * that goes into a single cell.
     *
     * @param question the question definition
     * @param answer   the answer, not null
     * @return the record
     */
    Map<String, String> collect(D question, A answer);
}
