package org.broadinstitute.dsm.model.elastic.export;

import java.util.Map;

public interface ExportableHelper {
    void setSource(Map<String, Object> source);
    void setRequestPayload(RequestPayload requestPayload);
}
