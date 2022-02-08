package org.broadinstitute.ddp.model.dsm;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;

/*
Singleton class to save Dsm Drug list
 */
public class DrugStore {

    private static Set<Drug> drugList;
    private static DrugStore instance;
    private static Object lockVar = "lock";

    private DrugStore() {
        drugList = new HashSet<Drug>();
    }

    public static DrugStore getInstance() {
        if (instance == null) {
            synchronized (lockVar) {
                if (instance == null) {
                    instance = new DrugStore();
                }
            }
        }
        return instance;
    }

    public synchronized void populateDrugList(List<String> drugs) {
        if (CollectionUtils.isEmpty(drugs)) {
            return;
        }
        drugList = new HashSet<Drug>();
        drugList.addAll(
                drugs.stream().map(
                    drugName -> new Drug(drugName, null)
                ).collect(Collectors.toSet())
        );
        drugList = Collections.unmodifiableSet(drugList);
    }

    public Set<Drug> getDrugList() {
        return drugList;
    }

}
