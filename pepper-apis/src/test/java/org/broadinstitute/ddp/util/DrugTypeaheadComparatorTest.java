package org.broadinstitute.ddp.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.model.dsm.Drug;

import org.junit.Before;
import org.junit.Test;

public class DrugTypeaheadComparatorTest {

    public static final String FIRST_RESULT = "Foo should match first";
    public static final String SECOND_RESULT = "This other foo should match second";
    public static final String FOURTH_RESULT = "This mcfoo should match fourth";
    public static final String THIRD_RESULT = "This crazy (foo) should match third";
    public static final String NONMATCHING_RESULT = "This shouldn't match";
    private Collection<Drug> drugs = new ArrayList<>();

    @Before
    public void setUp() {
        drugs.add(new Drug(THIRD_RESULT, null));
        drugs.add(new Drug(FIRST_RESULT, null));
        drugs.add(new Drug(NONMATCHING_RESULT, null));
        drugs.add(new Drug(SECOND_RESULT, null));
        drugs.add(new Drug(FOURTH_RESULT, null));
    }

    @Test
    public void testLeftMostMatchWins() {
        List<Drug> searchResults = drugs.stream().sorted(new DrugTypeaheadComparator("foo")).collect(Collectors.toList());

        assertEquals(drugs.size(), searchResults.size());
        assertEquals(FIRST_RESULT, searchResults.get(0).getName());
        assertEquals(SECOND_RESULT, searchResults.get(1).getName());
        assertEquals(THIRD_RESULT, searchResults.get(2).getName());
        assertEquals(FOURTH_RESULT, searchResults.get(3).getName());
        assertEquals(NONMATCHING_RESULT, searchResults.get(4).getName());
    }

    @Test
    public void testLexSort() {
        Collection<Drug> drugs = new ArrayList<>();
        drugs.add(new Drug("Drug Z", null));
        drugs.add(new Drug("Drug A", null));
        drugs.add(new Drug("Drug X", null));

        List<Drug> searchResults = drugs.stream().sorted(new DrugTypeaheadComparator("drug")).collect(Collectors.toList());

        assertEquals(3, searchResults.size());
        assertEquals("Drug A", searchResults.get(0).getName());
        assertEquals("Drug X", searchResults.get(1).getName());
        assertEquals("Drug Z", searchResults.get(2).getName());

        drugs.clear();
        drugs.add(new Drug("SuperDrug AB", null));
        drugs.add(new Drug("SuperDrug AA", null));

        searchResults = drugs.stream().sorted(new DrugTypeaheadComparator("drug")).collect(Collectors.toList());
        assertEquals(2, searchResults.size());
        assertEquals("SuperDrug AA", searchResults.get(0).getName());
        assertEquals("SuperDrug AB", searchResults.get(1).getName());
    }
}
