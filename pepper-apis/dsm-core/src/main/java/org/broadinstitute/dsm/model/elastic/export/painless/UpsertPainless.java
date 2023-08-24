package org.broadinstitute.dsm.model.elastic.export.painless;

import java.io.IOException;

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
    private final Generator generator;
    private final String index;
    private final ScriptBuilder scriptBuilder;
    private final QueryBuilder queryBuilder;


    public UpsertPainless(Generator generator, String index, ScriptBuilder scriptBuilder, QueryBuilder queryBuilder) {
        this.generator = generator;
        this.index = index;
        this.scriptBuilder = scriptBuilder;
        this.queryBuilder = queryBuilder;
    }

    @Override
    public void export() {
        RestHighLevelClient clientInstance = ElasticSearchUtil.getClientInstance();
        Script painless = new Script(ScriptType.INLINE, "painless", scriptBuilder.build(), generator.generate());
        UpdateByQueryRequest updateByQueryRequest = new UpdateByQueryRequest(index);
        updateByQueryRequest.setQuery(queryBuilder);
        updateByQueryRequest.setScript(painless);
        updateByQueryRequest.setMaxRetries(11);
        updateByQueryRequest.setRefresh(true);
        updateByQueryRequest.setAbortOnVersionConflict(true);

        Throwable ioException = null;
        for (int tryNum = 1; tryNum < 3; tryNum++) {
            ioException = executeExport(clientInstance, updateByQueryRequest);
            if (ioException == null) {
                break;
            }
            logger.info("Error occurred exporting data to ES on try number {} (retrying): {}", tryNum, ioException.toString());
        }
        if (ioException != null) {
            logger.error("Unable to connect to ElasticSearch: {}", ioException.getMessage());
            throw new DsmInternalError("Unable to connect to ElasticSearch", ioException);
        }
    }

    private Throwable executeExport(RestHighLevelClient clientInstance, UpdateByQueryRequest updateByQueryRequest) {
        String errorMsg = String.format("Error updating ES index %s for %s: ", index, generator.getPropertyName());
        try {
            BulkByScrollResponse res = update(clientInstance, updateByQueryRequest);
            if (!res.getBulkFailures().isEmpty()) {
                if (res.getVersionConflicts() == 0) {
                    throw new DsmInternalError(errorMsg + res);
                }
                RefreshResponse refreshResponse =
                        clientInstance.indices().refresh(new RefreshRequest(index), RequestOptions.DEFAULT);
                if (refreshResponse.getStatus() != RestStatus.OK) {
                    throw new DsmInternalError(String.format("ES index refresh failed for %s: %s", index, refreshResponse));
                }
                res = update(clientInstance, updateByQueryRequest);
                if (!res.getBulkFailures().isEmpty()) {
                    throw new DsmInternalError(errorMsg + res);
                }
            }
            return null;
        } catch (IOException e) {
            return e;
        } catch (ElasticsearchException e) {
            throw new DsmInternalError(errorMsg + e.toString(), e);
        }
    }

    private BulkByScrollResponse update(RestHighLevelClient client, UpdateByQueryRequest req) throws IOException {
        BulkByScrollResponse res = client.updateByQuery(req, RequestOptions.DEFAULT);
        logger.info("created/updated {} ES records for {}", getNumberOfUpserted(res),
                generator.getPropertyName());
        logger.info("BulkByScrollResponse: {}", res);
        return res;
    }

    private long getNumberOfUpserted(BulkByScrollResponse bulkByScrollResponse) {
        return Math.max(bulkByScrollResponse.getCreated(), bulkByScrollResponse.getUpdated());
    }

}
