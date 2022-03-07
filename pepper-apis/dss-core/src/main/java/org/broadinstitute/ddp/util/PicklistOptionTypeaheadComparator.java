package org.broadinstitute.ddp.util;

import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;

import java.util.Comparator;

/**
 * Comparator that can be used to sort results for
 * the picklist option typeahead.  The general approach is favoring leftwards
 * occurrences, in particular word starts, allowing for a starting
 * parentheses.
 */
public class PicklistOptionTypeaheadComparator implements Comparator<PicklistOption> {

    private final String searchTerm;
    StringSuggestionTypeaheadComparator stringComparator;

    /**
     * Create a new one, using the given search term
     * @param searchTerm a simple search term, provided by the
     *                   user.  Not a regex.
     */
    public PicklistOptionTypeaheadComparator(String searchTerm) {
        this.searchTerm = searchTerm.toUpperCase();
        this.stringComparator = new StringSuggestionTypeaheadComparator(searchTerm);
    }

    @Override
    public int compare(PicklistOption option1, PicklistOption option2) {
        String option1Name = option1.getOptionLabel().toUpperCase();
        String option2Name = option2.getOptionLabel().toUpperCase();
        return stringComparator.compare(option1Name, option2Name);
    }

}
