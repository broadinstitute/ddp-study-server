package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.Map;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.mapping.FieldTypeExtractor;
import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class UpsertPainlessFacade {

    private static final Logger logger = LoggerFactory.getLogger(UpsertPainlessFacade.class);
    private Object source;
    protected String uniqueIdentifier;
    protected String fieldName;
    protected Object fieldValue;
    Generator generator;
    Exportable upsertPainless;

    TypeExtractor<Map<String, String>> typeExtractor;

    UpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                         String fieldName, Object fieldValue) {
        this(source, uniqueIdentifier, fieldName, fieldValue);
        setGeneratorElseLogError(ddpInstanceDto);
        this.typeExtractor = buildFieldTypeExtractor(ddpInstanceDto);
        upsertPainless = new UpsertPainless(generator, ddpInstanceDto.getEsParticipantIndex(), buildScriptBuilder(), buildQueryBuilder());
    }

    UpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                         String fieldName, Object fieldValue, TypeExtractor<Map<String, String>> typeExtractor) {
        this(source, uniqueIdentifier, fieldName, fieldValue);
        setGeneratorElseLogError(ddpInstanceDto);
        this.typeExtractor = typeExtractor;
        upsertPainless = new UpsertPainless(generator, ddpInstanceDto.getEsParticipantIndex(), buildScriptBuilder(), buildQueryBuilder());
    }

    private UpsertPainlessFacade(Object source, String uniqueIdentifier,
                                 String fieldName, Object fieldValue) {
        this.source = source;
        this.uniqueIdentifier = uniqueIdentifier;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    private FieldTypeExtractor buildFieldTypeExtractor(DDPInstanceDto ddpInstanceDto) {
        FieldTypeExtractor fieldTypeExtractor = new FieldTypeExtractor();
        fieldTypeExtractor.setIndex(ddpInstanceDto.getEsParticipantIndex());
        fieldTypeExtractor.setFields(buildFieldFullName());
        return fieldTypeExtractor;
    }

    private void setGeneratorElseLogError(DDPInstanceDto ddpInstanceDto) {
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

    protected QueryBuilder buildQueryBuilder() {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        TermQueryBuilder term = new TermQueryBuilder(getFieldName(), fieldValue);
        boolQueryBuilder.must(term);
        return buildFinalQuery(boolQueryBuilder);
    }

    protected String getFieldName() {
        String fieldName = this.fieldName;
        if (ESObjectConstants.DOC_ID.equals(fieldName)) {
            return fieldName;
        } else if (isTextType(fieldName)) {
            fieldName = String.join(DBConstants.ALIAS_DELIMITER, fieldName, TypeParser.KEYWORD);
        }
        return String.join(DBConstants.ALIAS_DELIMITER, buildPath(), fieldName);
    }

    private boolean isTextType(String fieldName) {
        return TypeParser.TEXT.equals(typeExtractor.extract().get(fieldName));
    }

    protected String buildPath() {
        return String.join(DBConstants.ALIAS_DELIMITER, ESObjectConstants.DSM, generator.getPropertyName());
    }

    protected abstract QueryBuilder buildFinalQuery(BoolQueryBuilder boolQueryBuilder);

    private String buildFieldFullName() {
        String objectName = Util.capitalCamelCaseToLowerCamelCase(source.getClass().getSimpleName());
        return String.join(DBConstants.ALIAS_DELIMITER, BaseGenerator.DSM_OBJECT, objectName, fieldName);
    }

    public void export() {
        upsertPainless.export();
    }
}
