package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Base class for retrieving additional records from a different realm
 */
public abstract class AdditionalRecordsRetriever<A> {

    protected final String additionalRealm;

    AdditionalRecordsRetriever(String additionalRealm) {
        this.additionalRealm = additionalRealm;
    }

    /**
     * Retrieve additional records from the realm specified in the constructor
     * @return a map of records, keyed by participant guid
     */
    abstract Map<String, List<A>> retrieve();

    /**
     * Merge additional records (produced by AdditionalRecordsRetriever.retrieve) into a map of records
     * keyed by participant guid
     */
    public void mergeRecords(Map<String, List<A>> records) {
        Map<String, List<A>> additionalRecords = retrieve();
        additionalRecords.forEach((guid, newRecords) -> {
            if (records.containsKey(guid)) {
                List<A> merged = Stream.concat(records.get(guid).stream(), newRecords.stream()).toList();
                records.put(guid, merged);
            } else {
                records.put(guid, newRecords);
            }
        });
    }
}
