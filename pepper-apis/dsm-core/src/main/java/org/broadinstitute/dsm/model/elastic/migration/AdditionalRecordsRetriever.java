package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AdditionalRecordsRetriever<A> {

    protected final String additionalRealm;

    AdditionalRecordsRetriever(String additionalRealm) {
        this.additionalRealm = additionalRealm;
    }

    abstract Map<String, List<A>> retrieve();

    public void mergeRecords(Map<String, List<A>> records) {
        Map<String, List<A>> additionalRecords = retrieve();
        additionalRecords.forEach((guid, newRecords) -> {
            if (records.containsKey(guid)) {
                List<A> merged = Stream.concat(records.get(guid).stream(), newRecords.stream())
                        .collect(Collectors.toList());
                records.put(guid, merged);
            } else {
                records.put(guid, newRecords);
            }
        });
    }
}
