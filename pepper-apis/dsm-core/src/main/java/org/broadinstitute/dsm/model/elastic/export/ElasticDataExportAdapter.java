package org.broadinstitute.dsm.model.elastic.export;

import java.io.IOException;

import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticDataExportAdapter extends BaseExporter {

    private static final Logger logger = LoggerFactory.getLogger(ElasticDataExportAdapter.class);
    private WriteRequest.RefreshPolicy refreshPolicy = WriteRequest.RefreshPolicy.NONE;

    @Override
    public void export() {
        UpdateRequest updateRequest = new UpdateRequest(requestPayload.getIndex(), Util.DOC, requestPayload.getDocId())
                .doc(source)
                .setRefreshPolicy(refreshPolicy)
                .retryOnConflict(5);
        try {
            logger.debug("ES update source for doc ID {}: {}", requestPayload.getDocId(), source);
            UpdateResponse res = ElasticSearchUtil.getClientInstance().update(updateRequest, RequestOptions.DEFAULT);
            logger.info("ES UpdateResponse for doc ID {}: {}", requestPayload.getDocId(), res);
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new DsmInternalError("Error occurred while exporting data to ES", e);
        }
        logger.info("successfully exported data to ES");
    }

    /**
     * Set refresh policy for all the updates handled by this instance
     * Default refresh policy is WriteRequest.RefreshPolicy.NONE
     */
    public void setRefreshPolicy(WriteRequest.RefreshPolicy policy) {
        refreshPolicy = policy;
    }
}
