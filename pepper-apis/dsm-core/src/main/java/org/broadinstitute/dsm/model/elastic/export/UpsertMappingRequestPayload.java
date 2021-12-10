package org.broadinstitute.dsm.model.elastic.export;

import org.elasticsearch.client.indices.PutMappingRequest;

public class UpsertMappingRequestPayload {

    private String index;

    public UpsertMappingRequestPayload(String index) {
        this.index = index;
    }

    public PutMappingRequest getPutMappingRequest() {
        return new PutMappingRequest(index);
    }

}
