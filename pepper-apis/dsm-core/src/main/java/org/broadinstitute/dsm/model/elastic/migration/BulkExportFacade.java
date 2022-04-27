package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.elastic.Util.DOC;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BulkExportFacade {

    private static final Logger logger = LoggerFactory.getLogger(BulkExportFacade.class);

    private String index;
    private BulkRequest bulkRequest;

    public BulkExportFacade(String index) {
        this.index = index;
        this.bulkRequest = new BulkRequest();
    }

    public void addDataToRequest(Map mapToUpsert, String docId) {
        bulkRequest.add(createRequest(mapToUpsert, docId));
    }

    private UpdateRequest createRequest(Map mapToUpsert, String docId) {
        UpdateRequest updateRequest = new UpdateRequest(index, DOC, docId);
        updateRequest.doc(mapToUpsert);
        return updateRequest;
    }

    public long executeBulkUpsert() {
        RestHighLevelClient client = ElasticSearchUtil.getClientInstance();
        try {
            logger.info(String.format("attempting to upsert data for %s participants", bulkRequest.requests().size()));
            if (bulkRequest.requests().size() > 0) {
                BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                long successfullyExported = Arrays.stream(bulkResponse.getItems())
                        .filter(this::isSuccessfullyExported)
                        .count();
                logger.info(String.format("%s participants data has been successfully upserted", successfullyExported));
                return successfullyExported;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    private boolean isSuccessfullyExported(BulkItemResponse response) {
        return !response.isFailed();
    }


    public void clear() {
        this.bulkRequest.requests().clear();
    }
}
