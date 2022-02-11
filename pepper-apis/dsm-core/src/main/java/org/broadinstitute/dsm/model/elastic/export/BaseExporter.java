package org.broadinstitute.dsm.model.elastic.export;

import java.util.Map;

import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RestHighLevelClient;

public abstract class BaseExporter implements Exportable, ExportableHelper {

    protected RequestPayload requestPayload;
    protected Map<String, Object> source;

    @Override
    public void setRequestPayload(RequestPayload requestPayload) {
        this.requestPayload = requestPayload;
    }

    @Override
    public void setSource(Map<String, Object> source) {
        this.source = source;
    }

}
