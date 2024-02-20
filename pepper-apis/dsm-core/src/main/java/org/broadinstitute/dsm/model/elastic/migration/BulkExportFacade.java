package org.broadinstitute.dsm.model.elastic.migration;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.service.adminoperation.ExportLog;
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
    private final String index;
    private final BulkRequest bulkRequest;
    private final ExportLog exportLog;


    public BulkExportFacade(String index, ExportLog exportLog) {
        this.index = index;
        this.exportLog = exportLog;
        this.bulkRequest = new BulkRequest();
    }

    public void addDataToRequest(Map<String, Object> mapToUpsert, String docId) {
        bulkRequest.add(createRequest(mapToUpsert, docId));
    }

    public int size() {
        return bulkRequest.requests().size();
    }

    private UpdateRequest createRequest(Map<String, Object> mapToUpsert, String docId) {
        UpdateRequest updateRequest = new UpdateRequest(index, docId);
        updateRequest.doc(mapToUpsert);
        return updateRequest;
    }

    public long executeBulkUpsert() {
        if (bulkRequest.requests().isEmpty()) {
            return 0;
        }

        RestHighLevelClient client = ElasticSearchUtil.getClientInstance();
        try {
            logger.info("Upserting data for {} participants", bulkRequest.requests().size());
            BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
            long exportedCount = Arrays.stream(bulkResponse.getItems())
                    .filter(this::isSuccessfullyExported)
                    .count();
            logger.info("Upserted data for {} participants", exportedCount);
            updateExportLog(bulkResponse, exportedCount);
            return exportedCount;
        } catch (IOException e) {
            throw new DsmInternalError(e);
        }
    }

    private boolean isSuccessfullyExported(BulkItemResponse response) {
        return !response.isFailed();
    }

    private void updateExportLog(BulkResponse bulkResponse, long exportedCount) {
        if (exportLog != null) {
            exportLog.setParticipantCount(bulkRequest.requests().size());
            exportLog.setExportedCount((int) exportedCount);
            if (bulkResponse.hasFailures()) {
                exportLog.setMessage(bulkResponse.buildFailureMessage());
                exportLog.setStatus(ExportLog.Status.FAILURES);
            } else {
                exportLog.setStatus(ExportLog.Status.NO_FAILURES);
            }
        } else if (bulkResponse.hasFailures()) {
            logger.error(bulkResponse.buildFailureMessage());
        }
    }

    public void clear() {
        this.bulkRequest.requests().clear();
    }
}
