package org.broadinstitute.dsm.model.elastic.export;

import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RestHighLevelClient;

import java.util.Objects;

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
