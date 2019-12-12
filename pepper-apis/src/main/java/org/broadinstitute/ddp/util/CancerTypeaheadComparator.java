package org.broadinstitute.ddp.util;

import java.util.Comparator;
import java.util.regex.Pattern;

import org.broadinstitute.ddp.model.dsm.Cancer;

/**
 * Comparator that can be used to sort results for
 * the cancer typeahead.  The general approach is favoring leftwards
 * occurrences, in particular word starts, allowing for a starting
 * parentheses.
 */
public class CancerTypeaheadComparator implements Comparator<Cancer> {

    private final String searchTerm;

    /**
     * Create a new one, using the given search term
     * @param searchTerm a simple search term, provided by the
     *                   user.  Not a regex.
     */
    public CancerTypeaheadComparator(String searchTerm) {
        this.searchTerm = searchTerm.toUpperCase();
    }

    @Override
    public int compare(Cancer cancer1, Cancer cancer2) {
        String cancer1Name = cancer1.getName().toUpperCase();
        String cancer2Name = cancer2.getName().toUpperCase();
        boolean cancer1StartsWithSearchTerm = cancer1Name.startsWith(searchTerm);
        boolean cancer2StartsWithSearchTearm = cancer2Name.startsWith(searchTerm);

        // bias search results so that matches at the start of the name are favored
        if (cancer1StartsWithSearchTerm && cancer2StartsWithSearchTearm) {
            return cancer1Name.compareTo(cancer2Name);
        } else if (cancer1StartsWithSearchTerm) {
            return -1;
        } else if (cancer2StartsWithSearchTearm) {
            return 1;
        } else {
            // if the search is not at the start of the term, prefer hits that are at start of words
            Pattern startOfWordMatch = Pattern.compile("\\b(\\))*" + Pattern.quote(searchTerm));
            boolean cancer1MatchesAtWordStart = startOfWordMatch.matcher(cancer1Name).find();
            boolean cancer2MatchesAtWordStart = startOfWordMatch.matcher(cancer2Name).find();

            if (cancer1MatchesAtWordStart && !cancer2MatchesAtWordStart) {
                return -1;
            } else if (cancer2MatchesAtWordStart && !cancer1MatchesAtWordStart) {
                return 1;
            } else {
                // prefer left-most match
                int cancer1MatchPosition = cancer1Name.indexOf(searchTerm);
                int cancer2MatchPosition = cancer2Name.indexOf(searchTerm);

                if (cancer1MatchPosition > -1 && cancer2MatchPosition > -1) {
                    if (cancer1MatchPosition == cancer2MatchPosition) {
                        return cancer1Name.compareTo(cancer2Name);
                    } else {
                        return (cancer1MatchPosition < cancer2MatchPosition) ? -1 : 1;
                    }
                } else {
                    if (cancer1MatchPosition > -1) {
                        return -1;
                    } else if (cancer2MatchPosition > -1) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            }
        }
    }
}
