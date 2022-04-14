package org.broadinstitute.ddp.model.dsm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class DrugStoreTest {

    @Test
    public void testSingletonInstance() {
        List<String> sampleDrugList = new ArrayList<String>();
        sampleDrugList.add("Drug1");

        DrugStore instance1 = DrugStore.getInstance();
        Assert.assertNotNull(instance1);

        instance1.populateDrugList(sampleDrugList);
        Set<Drug> drugSet = instance1.getDrugList();
        Assert.assertNotNull(drugSet);
        Assert.assertEquals(1, drugSet.size());
        Assert.assertTrue(drugSet.stream().filter(drug -> drug.getName().equals("Drug1")).count() == 1);

        //check Singleton instance
        DrugStore instance2 = DrugStore.getInstance();
        Assert.assertNotNull(instance2);
        Set<Drug> drugSet2 = instance1.getDrugList();
        Assert.assertNotNull(drugSet2);
        Assert.assertEquals(1, drugSet2.size());
        Assert.assertTrue(drugSet2.stream().filter(drug -> drug.getName().equals("Drug1")).count() == 1);

        sampleDrugList.clear();
        sampleDrugList.add("New Drug1");
        sampleDrugList.add("New Drug2");
        instance1.populateDrugList(sampleDrugList);
        drugSet = instance1.getDrugList();
        Assert.assertNotNull(drugSet);
        Assert.assertEquals(2, drugSet.size());
        Assert.assertTrue(drugSet.stream().filter(drug -> drug.getName().equals("New Drug2")).count() == 1);

        instance2 = DrugStore.getInstance();
        drugSet2 = instance2.getDrugList();
        Assert.assertNotNull(drugSet2);
        Assert.assertEquals(2, drugSet2.size());
        Assert.assertTrue(drugSet.stream().filter(drug -> drug.getName().equals("New Drug1")).count() == 1);

    }

    @Test
    public void testPopulateDrugs() {
        List<String> sampleDrugList = new ArrayList<String>();
        sampleDrugList.add("Drug1");
        sampleDrugList.add("Drug2");
        sampleDrugList.add("Drug3");
        sampleDrugList.add("Drug1");

        DrugStore drugStoreInstance = DrugStore.getInstance();
        Assert.assertNotNull(drugStoreInstance);

        drugStoreInstance.populateDrugList(sampleDrugList);
        Set<Drug> drugSet = drugStoreInstance.getDrugList();
        Assert.assertNotNull(drugSet);
        Assert.assertEquals(3, drugSet.size());
        Assert.assertTrue(drugSet.stream().filter(drug -> drug.getName().equals("Drug2")).count() == 1);

        //handle null population
        drugStoreInstance.populateDrugList(null);
        drugSet = drugStoreInstance.getDrugList();
        Assert.assertNotNull(drugSet);
        Assert.assertEquals(3, drugSet.size());
        Assert.assertTrue(drugSet.stream().filter(drug -> drug.getName().equals("Drug2")).count() == 1);
    }

    @Test
    public void testDrugsUnmodifiable() {
        List<String> sampleDrugList = new ArrayList<String>();
        sampleDrugList.add("Drug4");
        sampleDrugList.add("Drug5");
        sampleDrugList.add("Drug6");
        sampleDrugList.add("Drug1");

        DrugStore drugStoreInstance = DrugStore.getInstance();
        Assert.assertNotNull(drugStoreInstance);

        drugStoreInstance.populateDrugList(sampleDrugList);
        Set<Drug> drugSet = drugStoreInstance.getDrugList();
        Assert.assertNotNull(drugSet);
        Assert.assertEquals(4, drugSet.size());

        //check returned drug set is unmodifiable
        try {
            drugSet.remove(drugSet.iterator().next());
            Assert.fail();
        } catch (UnsupportedOperationException e) {
            //ignore.. all good
        }
    }

}
