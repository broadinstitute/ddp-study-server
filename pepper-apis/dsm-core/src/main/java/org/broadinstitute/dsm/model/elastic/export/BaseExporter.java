package org.broadinstitute.dsm.model.elastic.export;

import java.util.Objects;

import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RestHighLevelClient;

public abstract class BaseExporter implements Exportable {

    protected RestHighLevelClient clientInstance = ElasticSearchUtil.getClientInstance();
    protected UpsertDataRequestPayload upsertDataRequestPayload;
    protected UpsertMappingRequestPayload upsertMappingRequestPayload;

    public void setUpdateRequestPayload(UpsertDataRequestPayload upsertDataRequestPayload) {
        this.upsertDataRequestPayload = Objects.requireNonNull(upsertDataRequestPayload);
    }

    public void setUpsertMappingRequestPayload(UpsertMappingRequestPayload upsertMappingRequestPayload) {
        this.upsertMappingRequestPayload = Objects.requireNonNull(upsertMappingRequestPayload);
    }
}
