package org.broadinstitute.ddp.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dto.CancerItem;

import org.broadinstitute.ddp.json.CancerSuggestionResponse;
import org.broadinstitute.ddp.model.suggestion.CancerSuggestion;
import org.broadinstitute.ddp.model.suggestion.PatternMatch;
import org.junit.Assert;
import org.junit.Test;

public class CancerStoreTest {

    @Test
    public void test_givenStoreIsInstantiated_whenItsInstanceIsRequested_thenItsNotNull() {
        CancerStore cancerStore = CancerStore.getInstance();
        Assert.assertNotNull(cancerStore);
    }

    @Test
    public void test_givenStoreIsPopulated_whenItsQueried_thenOnlyUniqueElementsAreReturned() {
        List<CancerItem> testCancerList = CancerItem.toCancerItemList(
                List.of("Cancer2", "Cancer1", "Cancer3", "Cancer1"), LanguageStore.ENGLISH_LANG_CODE);

        CancerStore cancerStore = CancerStore.getInstance();
        cancerStore.populate(testCancerList);

        Set<CancerItem> cancers = cancerStore.getCancerList(LanguageStore.ENGLISH_LANG_CODE);
        Assert.assertNotNull(cancers);
        Assert.assertEquals(3, cancers.size());
        Assert.assertEquals(1, cancers.stream().filter(cancerItem ->
                cancerItem.getCancerName().equals("Cancer1")).count());
    }

    @Test
    public void test_givenStoreIsPopulatedWithCancers_whenItsQueried_thenItReturnsExactlyThatCancers() {
        List<CancerItem> testCancerList = CancerItem.toCancerItemList(List.of("Cancer2", "Cancer1", "Cancer3"),
                LanguageStore.ENGLISH_LANG_CODE);
        CancerStore cancerStore = CancerStore.getInstance();
        cancerStore.populate(testCancerList);

        Set<CancerItem> cancers = cancerStore.getCancerList(LanguageStore.ENGLISH_LANG_CODE);
        Assert.assertNotNull(cancers);
        Assert.assertEquals(3, cancers.size());
        Assert.assertTrue(cancers.contains(new CancerItem("Cancer1", LanguageStore.ENGLISH_LANG_CODE)));
        Assert.assertTrue(cancers.contains(new CancerItem("Cancer2", LanguageStore.ENGLISH_LANG_CODE)));
        Assert.assertTrue(cancers.contains(new CancerItem("Cancer3", LanguageStore.ENGLISH_LANG_CODE)));
    }

    @Test
    public void test_givenCancerListIsNull_whenAttemptToAddItToStoreIsMade_thenItIsIgnored() {
        CancerStore cancerStore = CancerStore.getInstance();
        cancerStore.populate(CancerItem.toCancerItemList(List.of("Cancer1"), LanguageStore.ENGLISH_LANG_CODE));

        cancerStore.populate(null);

        Set<CancerItem> cancers = cancerStore.getCancerList(LanguageStore.ENGLISH_LANG_CODE);
        Assert.assertEquals(1, cancers.size());
        Assert.assertTrue(cancers.contains(new CancerItem("Cancer1", LanguageStore.ENGLISH_LANG_CODE)));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void test_givenStoreIsQueried_whenAttemptToModifyReturnedSetIsMade_thenExceptionIsThrown() {
        List<CancerItem> testCancerList = CancerItem.toCancerItemList(
                List.of("Cancer4", "Cancer5", "Cancer6", "Cancer1"), LanguageStore.ENGLISH_LANG_CODE);

        CancerStore cancerStore = CancerStore.getInstance();
        cancerStore.populate(testCancerList);
        Set<CancerItem> cancers = cancerStore.getCancerList(LanguageStore.ENGLISH_LANG_CODE);
        Assert.assertNotNull(cancers);
        cancers.remove(cancers.iterator().next());
    }

    @Test
    public void test_onlyCancersInSelectedLanguageAreReturned() {
        CancerItem spanishItem = new CancerItem("Something in Spanish", LanguageStore.SPANISH_LANG_CODE);
        CancerItem englishItem = new CancerItem("Something in English", LanguageStore.ENGLISH_LANG_CODE);

        CancerStore.getInstance().populate(List.of(spanishItem, englishItem));

        Set<CancerItem> spanishCancers = CancerStore.getInstance().getCancerList(LanguageStore.SPANISH_LANG_CODE);

        Assert.assertEquals(1, spanishCancers.size());
        Assert.assertEquals(spanishItem, spanishCancers.iterator().next());

        Set<CancerItem> englishCancers = CancerStore.getInstance().getCancerList(LanguageStore.ENGLISH_LANG_CODE);

        Assert.assertEquals(1, englishCancers.size());
        Assert.assertEquals(englishItem, englishCancers.iterator().next());
    }

    @Test
    public void testSpanishNonAccentedCharsFindAccentedCharsForSpanish() {
        List<CancerItem> cancers = new ArrayList<>();
        String cancer1 = "Cáncer esófagico";
        String cancer2 = "Cáncer pulmonar (de células no pequeñas y células pequeñas)";
        String cancer3 = "Cáncer gástrico (de estómago)";
        cancers.add(new CancerItem(cancer1, LanguageStore.SPANISH_LANG_CODE));
        cancers.add(new CancerItem(cancer2, LanguageStore.SPANISH_LANG_CODE));
        cancers.add(new CancerItem(cancer3, LanguageStore.SPANISH_LANG_CODE));

        CancerStore.getInstance().populate(cancers);

        CancerSuggestionResponse matches = CancerStore.getInstance().getCancerSuggestions("esofagico",
                LanguageStore.SPANISH_LANG_CODE, 100);
        Assert.assertEquals(1, matches.getResults().size());
        Assert.assertEquals(cancer1, matches.getResults().iterator().next().getCancer().getName());

        matches = CancerStore.getInstance().getCancerSuggestions("Cancer", LanguageStore.SPANISH_LANG_CODE, 100);
        Assert.assertEquals(3, matches.getResults().size());

        matches = CancerStore.getInstance().getCancerSuggestions("celulas", LanguageStore.SPANISH_LANG_CODE, 100);
        Assert.assertEquals(1, matches.getResults().size());
        Assert.assertEquals(cancer2, matches.getResults().iterator().next().getCancer().getName());

        matches = CancerStore.getInstance().getCancerSuggestions("tom", LanguageStore.SPANISH_LANG_CODE, 100);
        Assert.assertEquals(1, matches.getResults().size());
        Assert.assertEquals(cancer3, matches.getResults().iterator().next().getCancer().getName());
        CancerSuggestion suggestion = matches.getResults().iterator().next();
        PatternMatch match = suggestion.getMatches().iterator().next();
        Assert.assertEquals(1, suggestion.getMatches().size());
        Assert.assertEquals(22, match.getOffset());
        Assert.assertEquals(3, match.getLength());

        matches = CancerStore.getInstance().getCancerSuggestions("esófagico", LanguageStore.SPANISH_LANG_CODE, 100);
        Assert.assertEquals(1, matches.getResults().size());
        Assert.assertEquals(cancer1, matches.getResults().iterator().next().getCancer().getName());
        Assert.assertEquals(7, matches.getResults().iterator().next().getMatches().iterator().next().getOffset());

        matches = CancerStore.getInstance().getCancerSuggestions("gástricX", LanguageStore.SPANISH_LANG_CODE, 100);
        Assert.assertEquals(0, matches.getResults().size());

        matches = CancerStore.getInstance().getCancerSuggestions("gastricX", LanguageStore.SPANISH_LANG_CODE, 100);
        Assert.assertEquals(0, matches.getResults().size());

        matches = CancerStore.getInstance().getCancerSuggestions("gastric", LanguageStore.SPANISH_LANG_CODE, 100);
        Assert.assertEquals(1, matches.getResults().size());
        suggestion = matches.getResults().iterator().next();
        match = suggestion.getMatches().iterator().next();
        Assert.assertEquals(cancer3, suggestion.getCancer().getName());
        Assert.assertEquals(1, suggestion.getMatches().size());

        Assert.assertEquals(1, suggestion.getMatches().size());
        Assert.assertEquals(7, match.getOffset());
        Assert.assertEquals(7, match.getLength());









    }

}
