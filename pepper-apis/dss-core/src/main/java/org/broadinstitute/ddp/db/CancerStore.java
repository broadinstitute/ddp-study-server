package org.broadinstitute.ddp.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.broadinstitute.ddp.db.dto.CancerItem;

public class CancerStore {

    private static List<CancerItem> cancers;
    private static CancerStore instance;
    private static volatile Object lockVar = "lock";
    private final Map<String, Set<CancerItem>> cancersByLanguage = new HashMap<>();

    private CancerStore() {
        cancers = new ArrayList<>();
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

    public synchronized void populate(List<CancerItem> cancerItems) {
        if (cancerItems != null) {
            cancers = Collections.unmodifiableList(cancerItems);
            cancersByLanguage.clear();
            for (CancerItem cancer : cancers) {
                if (!cancersByLanguage.containsKey(cancer.getIsoLanguageCode())) {
                    cancersByLanguage.put(cancer.getIsoLanguageCode(), new LinkedHashSet<>());
                }
                cancersByLanguage.get(cancer.getIsoLanguageCode()).add(cancer);
            }
        }
    }

    /**
     * Returns all the cancers for the given language
     */
    public Set<CancerItem> getCancerList(String languageIsoCode) {
        if (cancersByLanguage.get(languageIsoCode) == null) {
            return Collections.emptySet();
        } else {
            return Collections.unmodifiableSet(cancersByLanguage.get(languageIsoCode));
        }
    }

}
