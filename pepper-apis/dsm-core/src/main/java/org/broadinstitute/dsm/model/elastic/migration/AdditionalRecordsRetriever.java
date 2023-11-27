package org.broadinstitute.dsm.model.elastic.migration;

import java.util.List;
import java.util.Map;

public interface AdditionalRecordsRetriever<A> {
    Map<String, List<A>> retrieve();
}
