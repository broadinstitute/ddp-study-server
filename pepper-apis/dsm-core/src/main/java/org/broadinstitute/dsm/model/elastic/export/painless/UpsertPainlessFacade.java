package org.broadinstitute.dsm.model.elastic.export.painless;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.elasticsearch.index.query.QueryBuilder;

public abstract class UpsertPainlessFacade {

    Generator generator;
    Exportable upsertPainless;
    protected String uniqueIdentifier;
    protected String fieldName;
    protected Object fieldValue;

    UpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                                String fieldName, Object fieldValue) {
        this.uniqueIdentifier = uniqueIdentifier;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        generator = new ParamsGenerator(source, ddpInstanceDto.getInstanceName());
        upsertPainless = new UpsertPainless(generator, ddpInstanceDto.getEsParticipantIndex(), buildScriptBuilder(), buildQueryBuilder());
    }

    protected abstract ScriptBuilder buildScriptBuilder();

    protected abstract QueryBuilder buildQueryBuilder();

    public void export() {
        upsertPainless.export();
    }

    public static UpsertPainlessFacade of(String alias, Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                                          String fieldName, Object fieldValue) {
        BaseGenerator.PropertyInfo propertyInfo = Util.TABLE_ALIAS_MAPPINGS.get(alias);
        return propertyInfo.isCollection()
                ? new NestedUpsertPainlessFacade(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue)
                : new SingleUpsertPainlessFacade(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue);
    }

}
