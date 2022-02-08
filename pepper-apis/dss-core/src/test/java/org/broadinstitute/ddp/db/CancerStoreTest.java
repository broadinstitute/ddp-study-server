package org.broadinstitute.ddp.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.model.dsm.Cancer;

import org.junit.Assert;
import org.junit.Test;

public class CancerStoreTest {

    @Test
    public void test_givenStoreIsInstantiated_whenItsInstanceIsRequested_thenItsNotNull() {
        List<String> testCancerList = new ArrayList<String>();
        testCancerList.addAll(Arrays.asList("Cancer2", "Cancer1", "Cancer3", "Cancer1"));
        CancerStore cancerStore = CancerStore.getInstance();
        Assert.assertNotNull(cancerStore);
    }

    @Test
    public void test_givenStoreIsPopulated_whenItsQueried_thenOnlyUniqueElementsAreReturned() {
        List<String> testCancerList = new ArrayList<String>();
        testCancerList.addAll(Arrays.asList("Cancer2", "Cancer1", "Cancer3", "Cancer1"));
        CancerStore cancerStore = CancerStore.getInstance();
        cancerStore.populate(testCancerList);

        Set<Cancer> cancers = cancerStore.getCancerList();
        Assert.assertNotNull(cancers);
        Assert.assertEquals(3, cancers.size());
        Assert.assertTrue(cancers.stream().filter(drug -> drug.getName().equals("Cancer1")).count() == 1);
    }

    @Test
    public void test_givenStoreIsPopulatedWithCancers_whenItsQueried_thenItReturnsExactlyThatCancers() {
        List<String> testCancerList = new ArrayList<String>();
        testCancerList.addAll(Arrays.asList("Cancer2", "Cancer1", "Cancer3"));
        CancerStore cancerStore = CancerStore.getInstance();
        cancerStore.populate(testCancerList);

        Set<Cancer> cancers = cancerStore.getCancerList();
        Assert.assertNotNull(cancers);
        Assert.assertEquals(3, cancers.size());
        Assert.assertTrue(cancers.contains(new Cancer("Cancer1", null)));
        Assert.assertTrue(cancers.contains(new Cancer("Cancer2", null)));
        Assert.assertTrue(cancers.contains(new Cancer("Cancer3", null)));
    }

    @Test
    public void test_givenCancerListIsNull_whenAttemptToAddItToStoreIsMade_thenItIsIgnored() {
        CancerStore cancerStore = CancerStore.getInstance();
        cancerStore.populate(Arrays.asList("Cancer1"));

        cancerStore.populate(null);

        Set<Cancer> cancers = cancerStore.getCancerList();
        Assert.assertEquals(1, cancers.size());
        Assert.assertTrue(cancers.contains(new Cancer("Cancer1", null)));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void test_givenStoreIsQueried_whenAttemptToModifyReturnedSetIsMade_thenExceptionIsThrown() {
        List<String> testCancerList = new ArrayList<String>();
        testCancerList.add("Cancer4");
        testCancerList.add("Cancer5");
        testCancerList.add("Cancer6");
        testCancerList.add("Cancer1");

        CancerStore cancerStore = CancerStore.getInstance();
        cancerStore.populate(testCancerList);
        Set<Cancer> cancers = cancerStore.getCancerList();
        Assert.assertNotNull(cancers);
        cancers.remove(cancers.iterator().next());
    }

}
