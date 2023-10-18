package org.broadinstitute.ddp.db;

import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dto.CancerItem;

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

}
