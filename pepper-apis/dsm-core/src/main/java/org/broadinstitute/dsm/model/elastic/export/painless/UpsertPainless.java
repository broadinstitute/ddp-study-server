package org.broadinstitute.dsm.model.elastic.export.painless;

import java.io.IOException;
import java.util.Map;

import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UpsertPainless implements Exportable {

    private static final Logger logger = LoggerFactory.getLogger(UpsertPainless.class);
    private Generator generator;
    private final String index;
    private ScriptBuilder scriptBuilder;
    private final QueryBuilder queryBuilder;


    public UpsertPainless(Generator generator, String index, ScriptBuilder scriptBuilder, QueryBuilder queryBuilder) {
        this.generator = generator;
        this.index = index;
        this.scriptBuilder = scriptBuilder;
        this.queryBuilder = queryBuilder;
    }

    public UpsertPainless(String index, QueryBuilder queryBuilder) {
        this.index = index;
        this.queryBuilder = queryBuilder;
    }

    @Override
    public void export() {
        export(scriptBuilder.build(), generator.generate(), generator.getPropertyName());
    }

    public void export(String script, Map<String, Object> source, String propertyName) {
        RestHighLevelClient clientInstance = ElasticSearchUtil.getClientInstance();
        Script painless = new Script(ScriptType.INLINE, "painless", script, source);
        logger.info("TEMP: upsert script: {}", painless);
        UpdateByQueryRequest updateByQueryRequest = new UpdateByQueryRequest(index);
        updateByQueryRequest.setQuery(queryBuilder);
        updateByQueryRequest.setScript(painless);
        updateByQueryRequest.setMaxRetries(11);
        updateByQueryRequest.setRefresh(true);
        updateByQueryRequest.setAbortOnVersionConflict(true);

        Throwable ioException = null;
        for (int tryNum = 1; tryNum < 3; tryNum++) {
            try {
                executeExport(clientInstance, updateByQueryRequest, propertyName);
                return;
            } catch (IOException e) {
                logger.info("Error occurred exporting data to ES on try number {} (retrying): {}", tryNum, e);
                ioException = e;
            }
        }
        // TODO: keeping error logging until we are sure exceptions are not swallowed in the call stack
        logger.error("Unable to connect to ElasticSearch", ioException);
        throw new DsmInternalError("Unable to connect to ElasticSearch", ioException);
    }

    private void executeExport(RestHighLevelClient clientInstance, UpdateByQueryRequest updateByQueryRequest,
                               String propertyName) throws IOException {
        String errorMsg = String.format("Error updating ES index %s for %s: ", index, propertyName);
        try {
            BulkByScrollResponse res = update(clientInstance, updateByQueryRequest, propertyName);
            if (!res.getBulkFailures().isEmpty()) {
                if (res.getVersionConflicts() == 0) {
                    throw new DsmInternalError(errorMsg + res);
                }
                RefreshResponse refreshResponse =
                        clientInstance.indices().refresh(new RefreshRequest(index), RequestOptions.DEFAULT);
                if (refreshResponse.getStatus() != RestStatus.OK) {
                    throw new DsmInternalError(String.format("ES index refresh failed for %s: %s", index, refreshResponse));
                }
                res = update(clientInstance, updateByQueryRequest, propertyName);
                if (!res.getBulkFailures().isEmpty()) {
                    throw new DsmInternalError(errorMsg + res);
                }
            }
        } catch (ElasticsearchException e) {
            throw new DsmInternalError(errorMsg + e.toString(), e);
        }
    }

    private static BulkByScrollResponse update(RestHighLevelClient client, UpdateByQueryRequest req,
                                               String propertyName) throws IOException {
        BulkByScrollResponse res = client.updateByQuery(req, RequestOptions.DEFAULT);
        logger.info("created/updated {} ES records for {}", getNumberOfUpserted(res), propertyName);
        logger.info("BulkByScrollResponse: {}", res);
        return res;
    }

    private static long getNumberOfUpserted(BulkByScrollResponse bulkByScrollResponse) {
        return Math.max(bulkByScrollResponse.getCreated(), bulkByScrollResponse.getUpdated());
    }

}
