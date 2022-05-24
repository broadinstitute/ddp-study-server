package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.Map;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class SingleUpsertPainlessFacade extends UpsertPainlessFacade {

    SingleUpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto,
                               String uniqueIdentifier, String fieldName, Object fieldValue, ScriptBuilder scriptBuilder) {
        super(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue, scriptBuilder);
    }

    SingleUpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                               String fieldName, Object fieldValue, TypeExtractor<Map<String, String>> typeExtractor,
                               ScriptBuilder scriptBuilder) {
        super(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue, typeExtractor, scriptBuilder);
    }

    @Override
    protected QueryBuilder buildFinalQuery(BoolQueryBuilder boolQueryBuilder) {
        return boolQueryBuilder;
    }
}
