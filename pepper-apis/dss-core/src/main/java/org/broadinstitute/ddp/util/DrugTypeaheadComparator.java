package org.broadinstitute.ddp.util;

import java.util.Comparator;

import org.broadinstitute.ddp.model.dsm.Drug;

/**
 * Comparator that can be used to sort results for
 * the drug typeahead.  The general approach is favoring leftwards
 * occurrences, in particular word starts, allowing for a starting
 * parentheses.
 */
public class DrugTypeaheadComparator implements Comparator<Drug> {

    private final String searchTerm;
    StringSuggestionTypeaheadComparator stringComparator;

    /**
     * Create a new one, using the given search term
     * @param searchTerm a simple search term, provided by the
     *                   user.  Not a regex.
     */
    public DrugTypeaheadComparator(String searchTerm) {
        this.searchTerm = searchTerm.toUpperCase();
        this.stringComparator = new StringSuggestionTypeaheadComparator(searchTerm);
    }

    @Override
    public int compare(Drug drug1, Drug drug2) {
        String drug1Name = drug1.getName().toUpperCase();
        String drug2Name = drug2.getName().toUpperCase();
        return stringComparator.compare(drug1Name, drug2Name);
    }
}
