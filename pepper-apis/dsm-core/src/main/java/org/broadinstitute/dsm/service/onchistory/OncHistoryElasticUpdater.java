package org.broadinstitute.dsm.service.onchistory;

import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_DETAIL;

import java.util.Map;

import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.export.ElasticDataExportAdapter;
import org.broadinstitute.dsm.model.elastic.export.RequestPayload;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.model.elastic.export.painless.ScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainless;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

public class OncHistoryElasticUpdater {

    private final ElasticDataExportAdapter exporter;
    private final String index;

    public OncHistoryElasticUpdater(String index) {
        this.index = index;
        this.exporter = new ElasticDataExportAdapter();
        this.exporter.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    }

    /**
     * Add or replace values in an elastic document for participant
     */
    public void update(Map<String, Object> source, String ddpParticipantId) {
        exporter.setRequestPayload(new RequestPayload(index, ddpParticipantId));
        exporter.setSource(source);
        try {
            exporter.export();
        } catch (DsmInternalError e) {
            throw e;
        } catch (Exception e) {
            String msg = String.format("Error updating ElasticSearch oncHistoryDetail index %s for participant %s",
                    index, ddpParticipantId);
            throw new DsmInternalError(msg, e);
        }
    }

    /**
     * Append values to an object in the elastic document for participant
     */
    public void updateAppend(Map<String, Object> source, String participantShortId) {

        OncHistoryGenerator generator = new OncHistoryGenerator(source);

        String scriptText = String.format("if (ctx._source.dsm.%s == null) {"
                + "ctx._source.dsm.%s = new ArrayList(); }"
                + "ctx._source.dsm.%s.add(params.%s);",
                ONC_HISTORY_DETAIL, ONC_HISTORY_DETAIL, ONC_HISTORY_DETAIL, ONC_HISTORY_DETAIL);
        OncHistoryScriptBuilder scriptBuilder = new OncHistoryScriptBuilder(scriptText);
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        queryBuilder.must(QueryBuilders.termsQuery("profile.hruid", participantShortId));

        try {
            UpsertPainless upsert = new UpsertPainless(generator, index, scriptBuilder, queryBuilder);
            upsert.export();
        } catch (DsmInternalError e) {
            throw e;
        } catch (Exception e) {
            String msg = String.format("Error updating ElasticSearch oncHistoryDetail index %s for participant %s",
                    index, participantShortId);
            throw new DsmInternalError(msg, e);
        }
    }

    private static class OncHistoryScriptBuilder implements ScriptBuilder {
        private final String script;

        public OncHistoryScriptBuilder(String script) {
            this.script = script;
        }

        public String build() {
            return script;
        }
    }

    private static class OncHistoryGenerator implements Generator {
        private final Map<String, Object> source;

        public OncHistoryGenerator(Map<String, Object> source) {
            this.source = source;
        }

        @Override
        public String getPropertyName() {
            return ONC_HISTORY_DETAIL;
        }

        public Map<String, Object> generate() {
            return source;
        }
    }
}
