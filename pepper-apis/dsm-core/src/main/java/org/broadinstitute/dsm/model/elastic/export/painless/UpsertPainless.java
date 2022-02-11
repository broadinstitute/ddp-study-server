package org.broadinstitute.dsm.model.elastic.export.painless;

import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class UpsertPainless implements Exportable {

    private Generator generator;
    private String index;
    private ScriptBuilder scriptBuilder;
    private QueryBuilder queryBuilder;

    private static final Logger logger = LoggerFactory.getLogger(UpsertPainless.class);


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
        try {
            clientInstance.updateByQuery(updateByQueryRequest, RequestOptions.DEFAULT);
            logger.info(String.format("Successfully updated ES data for %s", generator.getPropertyName()));
        } catch (IOException e) {
            throw new RuntimeException("Error occurred while exporting data to ES", e);
        }
    }

}
