package org.broadinstitute.dsm.model.elastic.export.painless;

import java.io.IOException;

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
        logger.info("Export isAbortOnVersionConflict: " + updateByQueryRequest.isAbortOnVersionConflict());
        for (int tryNum = 1; tryNum < 3; tryNum++) {
            if (executeExport(clientInstance, updateByQueryRequest, tryNum)) {
                break;
            }
        }
    }

    private boolean executeExport(RestHighLevelClient clientInstance, UpdateByQueryRequest updateByQueryRequest, int i) {
        try {
            BulkByScrollResponse bulkByScrollResponse = clientInstance.updateByQuery(updateByQueryRequest, RequestOptions.DEFAULT);
            logger.info(String.format("created/updated %s records in ES data for %s", getNumberOfUpserted(bulkByScrollResponse),
                    generator.getPropertyName()));
            return true;
        } catch (IOException e) {
            logger.info("Error occurred while exporting data to ES, on try number " + i, e);
            return false;
        }
    }

    private long getNumberOfUpserted(BulkByScrollResponse bulkByScrollResponse) {
        return Math.max(bulkByScrollResponse.getCreated(), bulkByScrollResponse.getUpdated());
    }

}
