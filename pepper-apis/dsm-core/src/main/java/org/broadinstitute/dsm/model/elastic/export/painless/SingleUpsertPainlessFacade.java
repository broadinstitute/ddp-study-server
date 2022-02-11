package org.broadinstitute.dsm.model.elastic.export.painless;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

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
    protected QueryBuilder buildQueryBuilder() {
        return new MatchQueryBuilder(fieldName, fieldValue);
    }
}
