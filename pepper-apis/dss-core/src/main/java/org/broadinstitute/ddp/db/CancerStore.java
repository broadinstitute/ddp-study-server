package org.broadinstitute.ddp.db;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;

import org.broadinstitute.ddp.model.dsm.Cancer;

public class CancerStore {

    private static Set<Cancer> cancers;
    private static CancerStore instance;
    private static volatile Object lockVar = "lock";

    private CancerStore() {
        cancers = new HashSet<Cancer>();
    }

    public static CancerStore getInstance() {
        if (instance == null) {
            synchronized (lockVar) {
                if (instance == null) {
                    instance = new CancerStore();
                }
            }
        }
        return instance;
    }

    public synchronized void populate(List<String> cancerNames) {
        if (CollectionUtils.isEmpty(cancerNames)) {
            return;
        }
        cancers = new HashSet<>();
        cancers.addAll(
                cancerNames.stream().map(
                    cancerName -> new Cancer(cancerName, null)
                ).collect(Collectors.toSet())
        );
        cancers = Collections.unmodifiableSet(cancers);
    }

    public Set<Cancer> getCancerList() {
        return cancers;
    }

}
