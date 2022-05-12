package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.Map;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class NestedUpsertPainlessFacade extends UpsertPainlessFacade {

    NestedUpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto,
                               String uniqueIdentifier, String fieldName, Object fieldValue) {
        super(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue);
    }

    NestedUpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                               String fieldName, Object fieldValue, TypeExtractor<Map<String, String>> typeExtractor) {
        super(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue, typeExtractor);
    }

    @Override
    protected ScriptBuilder buildScriptBuilder() {
        return new NestedScriptBuilder(generator.getPropertyName(), uniqueIdentifier);
    }

    @Override
    protected QueryBuilder buildFinalQuery(BoolQueryBuilder boolQueryBuilder) {
        if (isDocId()) {
            return boolQueryBuilder;
        }
        return new NestedQueryBuilder(buildPath(), boolQueryBuilder, ScoreMode.Avg);
    }

    private boolean isDocId() {
        return ESObjectConstants.DOC_ID.equals(uniqueIdentifier) || ESObjectConstants.DOC_ID.equals(getFieldName());
    }

}
