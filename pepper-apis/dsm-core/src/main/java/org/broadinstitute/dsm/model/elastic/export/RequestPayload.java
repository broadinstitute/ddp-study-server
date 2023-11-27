package org.broadinstitute.dsm.model.elastic.export;

import org.apache.commons.lang3.StringUtils;

public class RequestPayload {

    private String index;
    private String docId;

    public RequestPayload(String index, String docId) {
        this.index = index;
        this.docId = docId;
    }

    public RequestPayload(String index) {
        this(index, StringUtils.EMPTY);
    }

    public String getIndex() {
        return index;
    }

    public String getDocId() {
        return docId;
    }
}
