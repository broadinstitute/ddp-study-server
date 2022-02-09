package org.broadinstitute.ddp.util;

import java.util.Comparator;
import java.util.regex.Pattern;

import org.broadinstitute.ddp.model.dsm.Drug;

/**
 * Comparator that can be used to sort results for
 * the drug typeahead.  The general approach is favoring leftwards
 * occurrences, in particular word starts, allowing for a starting
 * parentheses.
 */
public class DrugTypeaheadComparator implements Comparator<Drug> {

    private final String searchTerm;

    /**
     * Create a new one, using the given search term
     * @param searchTerm a simple search term, provided by the
     *                   user.  Not a regex.
     */
    public DrugTypeaheadComparator(String searchTerm) {
        this.searchTerm = searchTerm.toUpperCase();
    }

    @Override
    public int compare(Drug drug1, Drug drug2) {
        String drug1Name = drug1.getName().toUpperCase();
        String drug2Name = drug2.getName().toUpperCase();
        boolean drug1StartsWithSearchTerm = drug1Name.startsWith(searchTerm);
        boolean drug2StartsWithSearchTearm = drug2Name.startsWith(searchTerm);


        // bias search results so that matches at the start of the name are favored
        if (drug1StartsWithSearchTerm && !drug2StartsWithSearchTearm) {
            return -1;
        } else if (drug2StartsWithSearchTearm && !drug1StartsWithSearchTerm) {
            return 1;
        } else {
            if (drug1StartsWithSearchTerm && drug2StartsWithSearchTearm) {
                return drug1Name.compareTo(drug2Name);
            } else {
                // if the search is not at the start of the term, prefer hits that are at
                // start of words
                Pattern startOfWordMatch = Pattern.compile("\\b(\\))*" + Pattern.quote(searchTerm));
                boolean drug1MatchesAtWordStart = startOfWordMatch.matcher(drug1Name).find();
                boolean drug2MatchesAtWordStart = startOfWordMatch.matcher(drug2Name).find();

                if (drug1MatchesAtWordStart && !drug2MatchesAtWordStart) {
                    return -1;
                } else if (drug2MatchesAtWordStart && !drug1MatchesAtWordStart) {
                    return 1;
                } else {
                    // prefer left-most match
                    int drug1MatchPosition = drug1Name.indexOf(searchTerm);
                    int drug2MatchPosition = drug2Name.indexOf(searchTerm);

                    if (drug1MatchPosition > -1 && drug2MatchPosition > -1) {
                        if (drug1MatchPosition == drug2MatchPosition) {
                            return drug1Name.compareTo(drug2Name);
                        } else {
                            return (drug1MatchPosition < drug2MatchPosition) ? -1 : 1;
                        }
                    } else {
                        if (drug1MatchPosition > -1) {
                            return -1;
                        } else if (drug2MatchPosition > -1) {
                            return 1;
                        } else {
                            return 0;
                        }
                    }
                }

            }
        }
    }
}
