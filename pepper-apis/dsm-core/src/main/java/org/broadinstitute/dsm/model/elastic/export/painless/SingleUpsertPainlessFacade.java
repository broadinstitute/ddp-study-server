package org.broadinstitute.dsm.model.elastic.export.painless;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;

public class SingleUpsertPainlessFacade extends UpsertPainlessFacade {

    SingleUpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto,
                               String uniqueIdentifier, String fieldName, Object fieldValue) {
        super(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue);
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
