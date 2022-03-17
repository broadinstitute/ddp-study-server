package org.broadinstitute.ddp.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

public class StringSuggestionTypeaheadComparatorTest {

    public static final String FIRST_RESULT = "Foo should match first";
    public static final String SECOND_RESULT = "This other foo should match second";
    public static final String FOURTH_RESULT = "This mcfoo should match fourth";
    public static final String THIRD_RESULT = "This crazy (foo) should match third";
    public static final String NON_MATCHING_RESULT = "This shouldn't match";
    private Collection<String> names = new ArrayList<>();


    @Before
    public void setUp() {
        names.add(THIRD_RESULT);
        names.add(FIRST_RESULT);
        names.add(NON_MATCHING_RESULT);
        names.add(SECOND_RESULT);
        names.add(FOURTH_RESULT);
    }

    @Test
    public void testLeftMostMatchWins() {
        List<String> searchResults = names.stream().sorted(new StringSuggestionTypeaheadComparator("foo")).collect(Collectors.toList());

        assertEquals(names.size(), searchResults.size());
        assertEquals(FIRST_RESULT, searchResults.get(0));
        assertEquals(SECOND_RESULT, searchResults.get(1));
        assertEquals(THIRD_RESULT, searchResults.get(2));
        assertEquals(FOURTH_RESULT, searchResults.get(3));
        assertEquals(NON_MATCHING_RESULT, searchResults.get(4));
    }

    @Test
    public void testLexSort() {
        Collection<String> drugs = new ArrayList<>();
        drugs.add("Drug Z");
        drugs.add("Drug A");
        drugs.add("Drug X");

        List<String> searchResults = drugs.stream().sorted(new StringSuggestionTypeaheadComparator("drug")).collect(Collectors.toList());

        assertEquals(3, searchResults.size());
        assertEquals("Drug A", searchResults.get(0));
        assertEquals("Drug X", searchResults.get(1));
        assertEquals("Drug Z", searchResults.get(2));

        drugs.clear();
        drugs.add("SuperDrug AB");
        drugs.add("SuperDrug AA");

        searchResults = drugs.stream().sorted(new StringSuggestionTypeaheadComparator("drug")).collect(Collectors.toList());
        assertEquals(2, searchResults.size());
        assertEquals("SuperDrug AA", searchResults.get(0));
        assertEquals("SuperDrug AB", searchResults.get(1));
    }
}
