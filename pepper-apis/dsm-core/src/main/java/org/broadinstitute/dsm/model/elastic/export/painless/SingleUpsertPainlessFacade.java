package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.Map;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class SingleUpsertPainlessFacade extends UpsertPainlessFacade {

    SingleUpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier, String fieldName, Object fieldValue) {
        super(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue);
    }

    SingleUpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                               String fieldName, Object fieldValue, TypeExtractor<Map<String, String>> typeExtractor) {
        super(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue, typeExtractor);
    }

    @Override
    protected ScriptBuilder buildScriptBuilder() {
        return new SingleScriptBuilder(generator.getPropertyName());
    }

    @Override
    protected QueryBuilder buildFinalQuery(BoolQueryBuilder boolQueryBuilder) {
        return boolQueryBuilder;
    }
}
