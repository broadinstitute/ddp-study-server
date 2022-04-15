package org.broadinstitute.dsm.model.elastic.export.painless;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class UpsertPainlessFacade {

    private static final Logger logger = LoggerFactory.getLogger(UpsertPainlessFacade.class);
    protected String uniqueIdentifier;
    protected String fieldName;
    protected Object fieldValue;
    Generator generator;
    Exportable upsertPainless;

    UpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                         String fieldName, Object fieldValue) {
        this.uniqueIdentifier = uniqueIdentifier;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        setGeneratorElseLogError(source, ddpInstanceDto);
        upsertPainless = new UpsertPainless(generator, ddpInstanceDto.getEsParticipantIndex(), buildScriptBuilder(), buildQueryBuilder());
    }

    private void setGeneratorElseLogError(Object source, DDPInstanceDto ddpInstanceDto) {
        try {
            generator = new ParamsGenerator(source, ddpInstanceDto.getInstanceName());
        } catch (NullPointerException npe) {
            logger.error("ddp instance is null, probably instance with such realm does not exist");
        }
    }

    public static UpsertPainlessFacade of(String alias, Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                                          String fieldName, Object fieldValue) {
        BaseGenerator.PropertyInfo propertyInfo = Util.TABLE_ALIAS_MAPPINGS.get(alias);
        return propertyInfo.isCollection()
                ? new NestedUpsertPainlessFacade(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue)
                : new SingleUpsertPainlessFacade(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue);
    }

    protected abstract ScriptBuilder buildScriptBuilder();

    protected abstract QueryBuilder buildQueryBuilder();

    public void export() {
        upsertPainless.export();
    }

}