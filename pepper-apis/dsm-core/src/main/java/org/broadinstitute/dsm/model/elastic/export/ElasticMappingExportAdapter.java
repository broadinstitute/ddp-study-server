package org.broadinstitute.dsm.model.elastic.export;

import java.io.IOException;

import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticMappingExportAdapter extends BaseExporter {

    private static final Logger logger = LoggerFactory.getLogger(ElasticMappingExportAdapter.class);

    @Override
    public void export() {
        String esIndex = requestPayload.getIndex();
        logger.info("initialize exporting mapping to ES with index: " + esIndex);
        PutMappingRequest putMappingRequest = new PutMappingRequest(esIndex);
        putMappingRequest.source(source);
        try {
            ElasticSearchUtil.getClientInstance().indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Error occurred while updating mapping to ES with index: " + esIndex, e);
        }
        logger.info("successfully exported mapping to ES with index: " + esIndex);
    }
}
