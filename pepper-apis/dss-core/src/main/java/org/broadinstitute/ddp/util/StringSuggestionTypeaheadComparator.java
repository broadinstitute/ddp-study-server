package org.broadinstitute.ddp.util;

import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Comparator that can be used to sort results for
 * the picklist option typeahead.  The general approach is favoring leftwards
 * occurrences, in particular word starts, allowing for a starting
 * parentheses.
 */
public class StringSuggestionTypeaheadComparator implements Comparator<String> {

    private final String searchTerm;
    private static final String MATCH_REGEX = "\\b(\\))*";

    /**
     * Create a new one, using the given search term
     * @param searchTerm a simple search term, provided by the
     *                   user.  Not a regex.
     */
    public StringSuggestionTypeaheadComparator(String searchTerm) {
        this.searchTerm = searchTerm.toUpperCase();
    }

    @Override
    public int compare(String string1, String string2) {
        String string1Name = string1.toUpperCase();
        String string2Name = string2.toUpperCase();
        boolean string1StartsWithSearchTerm = string1Name.startsWith(searchTerm);
        boolean string2StartsWithSearchTerm = string2Name.startsWith(searchTerm);

        // bias search results so that matches at the start of the name are favored
        if (string1StartsWithSearchTerm && string2StartsWithSearchTerm) {
            return string1Name.compareTo(string2Name);
        } else if (string1StartsWithSearchTerm) {
            return -1;
        } else if (string2StartsWithSearchTerm) {
            return 1;
        } else {
            // if the search is not at the start of the term, prefer hits that are at start of words
            Pattern startOfWordMatch = Pattern.compile(MATCH_REGEX + Pattern.quote(searchTerm));
            boolean string1MatchesAtWordStart = startOfWordMatch.matcher(string1Name).find();
            boolean string2MatchesAtWordStart = startOfWordMatch.matcher(string2Name).find();

            if (string1MatchesAtWordStart && !string2MatchesAtWordStart) {
                return -1;
            } else if (string2MatchesAtWordStart && !string1MatchesAtWordStart) {
                return 1;
            } else {
                // prefer left-most match
                int string1MatchPosition = string1Name.indexOf(searchTerm);
                int string2MatchPosition = string2Name.indexOf(searchTerm);

                if (string1MatchPosition > -1 && string2MatchPosition > -1) {
                    if (string1MatchPosition == string2MatchPosition) {
                        return string1Name.compareTo(string2Name);
                    } else {
                        return (string1MatchPosition < string2MatchPosition) ? -1 : 1;
                    }
                } else {
                    if (string1MatchPosition > -1) {
                        return -1;
                    } else if (string2MatchPosition > -1) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            }
        }
    }
}
