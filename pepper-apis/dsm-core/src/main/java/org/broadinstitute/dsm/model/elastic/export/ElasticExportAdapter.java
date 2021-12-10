package org.broadinstitute.dsm.model.elastic.export;


import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class ElasticExportAdapter extends BaseExporter {

    private static final Logger logger = LoggerFactory.getLogger(ElasticExportAdapter.class);

    @Override
    public void exportData(Map<String, Object> data) {
        logger.info("initialize exporting data to ES");
        UpdateRequest updateRequest = upsertDataRequestPayload.getUpdateRequest(data);
        try {
            clientInstance.update(updateRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Error occurred while exporting data to ES", e);
        }
        logger.info("successfully exported data to ES");
    }

    @Override
    public void exportMapping(Map<String, Object> mapping) {
        logger.info("initialize exporting mapping to ES");
        PutMappingRequest putMappingRequest = upsertMappingRequestPayload.getPutMappingRequest();
        putMappingRequest.source(mapping);
        try {
            clientInstance.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException("Error occurred while updating mapping to ES", e);
        }
        logger.info("successfully exported mapping to ES");
    }
}
