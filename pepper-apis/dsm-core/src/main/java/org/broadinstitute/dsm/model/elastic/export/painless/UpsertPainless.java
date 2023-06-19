package org.broadinstitute.dsm.model.elastic.export.painless;

import java.io.IOException;

import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UpsertPainless implements Exportable {

    private static final Logger logger = LoggerFactory.getLogger(UpsertPainless.class);
    private Generator generator;
    private String index;
    private ScriptBuilder scriptBuilder;
    private QueryBuilder queryBuilder;


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
        updateByQueryRequest.setMaxRetries(5);
        updateByQueryRequest.setRefresh(true);
        updateByQueryRequest.setAbortOnVersionConflict(false);

        Throwable ioException = null;
        for (int tryNum = 1; tryNum < 3; tryNum++) {
            ioException = executeExport(clientInstance, updateByQueryRequest, tryNum);
            if (ioException == null) {
                break;
            }
        }
        if (ioException != null) {
            logger.error("Unable to connect to ElasticSearch: {}", ioException.getMessage());
            throw new DsmInternalError("Unable to connect to ElasticSearch", ioException);
        }
    }

    private Throwable executeExport(RestHighLevelClient clientInstance, UpdateByQueryRequest updateByQueryRequest, int i) {
        try {
            BulkByScrollResponse bulkByScrollResponse = clientInstance.updateByQuery(updateByQueryRequest, RequestOptions.DEFAULT);
            logger.info("created/updated {} ES records for {}", getNumberOfUpserted(bulkByScrollResponse),
                    generator.getPropertyName());
            return null;
        } catch (IOException e) {
            logger.info("Error occurred while exporting data to ES, on try number " + i, e);
            return e;
        } catch (Exception e) {
            // TODO adding this since callers seem to be ignoring exceptions from this method
            // once that is cleaned up we can get rid of this - DC
            logger.error("Error updating ES index {}: {}", index, e.toString());
            throw e;
        }
    }

    private long getNumberOfUpserted(BulkByScrollResponse bulkByScrollResponse) {
        return Math.max(bulkByScrollResponse.getCreated(), bulkByScrollResponse.getUpdated());
    }

}
