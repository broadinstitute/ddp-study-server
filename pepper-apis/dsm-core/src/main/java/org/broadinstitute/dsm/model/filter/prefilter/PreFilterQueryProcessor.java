package org.broadinstitute.dsm.model.filter.prefilter;

import java.util.Map;

public interface PreFilterQueryProcessor {
    Map<String, String> update(String query);
}
