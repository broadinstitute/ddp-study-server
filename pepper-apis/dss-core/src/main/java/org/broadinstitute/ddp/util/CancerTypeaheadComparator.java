package org.broadinstitute.ddp.util;

import java.util.Comparator;

import org.broadinstitute.ddp.model.dsm.Cancer;

/**
 * Comparator that can be used to sort results for
 * the cancer typeahead.  The general approach is favoring leftwards
 * occurrences, in particular word starts, allowing for a starting
 * parentheses.
 */
public class CancerTypeaheadComparator implements Comparator<Cancer> {

    private final String searchTerm;
    StringSuggestionTypeaheadComparator stringComparator;

    /**
     * Create a new one, using the given search term
     * @param searchTerm a simple search term, provided by the
     *                   user.  Not a regex.
     */
    public CancerTypeaheadComparator(String searchTerm) {
        this.searchTerm = searchTerm.toUpperCase();
        this.stringComparator = new StringSuggestionTypeaheadComparator(searchTerm);
    }

    @Override
    public int compare(Cancer cancer1, Cancer cancer2) {
        String cancer1Name = cancer1.getName().toUpperCase();
        String cancer2Name = cancer2.getName().toUpperCase();
        return stringComparator.compare(cancer1Name, cancer2Name);
    }
}
