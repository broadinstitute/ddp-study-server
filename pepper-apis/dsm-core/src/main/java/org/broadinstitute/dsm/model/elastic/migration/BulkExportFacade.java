package org.broadinstitute.dsm.model.elastic.migration;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.service.adminoperation.ExportLog;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;

@Slf4j
public class BulkExportFacade {
    private final String index;
    private final BulkRequest bulkRequest;
    private final ExportLogger exportLogger;


    public BulkExportFacade(String index, ExportLogger exportLogger) {
        this.index = index;
        this.exportLogger = exportLogger;
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
            BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
            List<String> successIds = Arrays.stream(bulkResponse.getItems()).filter(res -> !res.isFailed())
                    .map(BulkItemResponse::getId).toList();
            List<String> failureIds = Arrays.stream(bulkResponse.getItems())
                    .filter(BulkItemResponse::isFailed)
                    .map(BulkItemResponse::getId).toList();
            int exportedCount = successIds.size();
            log.info("Upserted data for {} participants with {} failures", exportedCount, failureIds.size());
            updateExportLog(bulkResponse, successIds, failureIds);
            return exportedCount;
        } catch (IOException e) {
            throw new DsmInternalError(e);
        }
    }

    private void updateExportLog(BulkResponse bulkResponse, List<String> successIds, List<String> failureIds) {
        if (exportLogger.useExportLog()) {
            ExportLog exportLog = new ExportLog(exportLogger.getEntity());
            exportLogger.addLog(exportLog);
            exportLog.setParticipantCount(bulkRequest.requests().size());
            exportLog.setExportedCount(successIds.size());
            if (exportLogger.recordSuccessIds()) {
                exportLog.setSuccessIds(successIds);
            }
            exportLog.setFailureIds(failureIds);
            if (bulkResponse.hasFailures()) {
                exportLog.setMessage(bulkResponse.buildFailureMessage());
                exportLog.setStatus(ExportLog.Status.FAILURES);
            } else {
                exportLog.setStatus(ExportLog.Status.NO_FAILURES);
            }
        } else if (bulkResponse.hasFailures()) {
            log.error("Failures while exporting %s: %s".formatted(
                    exportLogger.getEntity(), bulkResponse.buildFailureMessage()));
        }
    }

    public void clear() {
        this.bulkRequest.requests().clear();
    }
}
