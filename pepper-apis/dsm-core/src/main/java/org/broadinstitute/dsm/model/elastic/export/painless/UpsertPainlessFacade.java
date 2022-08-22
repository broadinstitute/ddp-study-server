package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.mapping.FieldTypeExtractor;
import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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

    public UpsertPainlessFacade() {}

    UpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                         String fieldName, Object fieldValue, ScriptBuilder scriptBuilder) {
        this(source, uniqueIdentifier, fieldName, fieldValue, ddpInstanceDto);
        buildAndSetFieldTypeExtractor(ddpInstanceDto);
        buildAndSetUpsertPainless(ddpInstanceDto, scriptBuilder);
    }


    UpsertPainlessFacade(Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                         String fieldName, Object fieldValue,
                         TypeExtractor<Map<String, String>> typeExtractor, ScriptBuilder scriptBuilder) {
        this(source, uniqueIdentifier, fieldName, fieldValue, ddpInstanceDto);
        this.typeExtractor = typeExtractor;
        buildAndSetUpsertPainless(ddpInstanceDto, scriptBuilder);
    }

    private UpsertPainlessFacade(Object source, String uniqueIdentifier,
                                 String fieldName, Object fieldValue, DDPInstanceDto ddpInstanceDto) {
        this.source = source;
        this.uniqueIdentifier = uniqueIdentifier;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        setGeneratorElseLogError(ddpInstanceDto);
    }

    public void setSource(Object source) {
        this.source = source;
    }

    public void setUniqueIdentifier(String uniqueIdentifier) {
        this.uniqueIdentifier = uniqueIdentifier;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setFieldValue(Object fieldValue) {
        this.fieldValue = fieldValue;
    }

    public void buildAndSetUpsertPainless(DDPInstanceDto ddpInstanceDto, ScriptBuilder scriptBuilder) {
        this.upsertPainless = new UpsertPainless(generator, ddpInstanceDto.getEsParticipantIndex(),
                fillScriptBuilder(scriptBuilder), buildQueryBuilder());
    }

    private ScriptBuilder fillScriptBuilder(ScriptBuilder scriptBuilder) {
        scriptBuilder.setPropertyName(generator.getPropertyName());
        scriptBuilder.setUniqueIdentifier(uniqueIdentifier);
        return scriptBuilder;
    }

    public void buildAndSetFieldTypeExtractor(DDPInstanceDto ddpInstanceDto) {
        FieldTypeExtractor fieldTypeExtractor = new FieldTypeExtractor();
        fieldTypeExtractor.setIndex(ddpInstanceDto.getEsParticipantIndex());
        fieldTypeExtractor.setFields(buildFieldFullName());
        this.typeExtractor = fieldTypeExtractor;
    }

    public void setGeneratorElseLogError(DDPInstanceDto ddpInstanceDto) {
        try {
            generator = new ParamsGeneratorFactory(source, ddpInstanceDto.getInstanceName()).instance();
        } catch (NullPointerException npe) {
            logger.error("ddp instance is null, probably instance with such realm does not exist");
        }
    }

    public static UpsertPainlessFacade of(String alias, Object source, DDPInstanceDto ddpInstanceDto, String uniqueIdentifier,
                                          String fieldName, Object fieldValue, ScriptBuilder scriptBuilder) {
        PropertyInfo propertyInfo = PropertyInfo.of(alias);
        return propertyInfo.isCollection()
                ? new NestedUpsertPainlessFacade(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue, scriptBuilder)
                : new SingleUpsertPainlessFacade(source, ddpInstanceDto, uniqueIdentifier, fieldName, fieldValue, scriptBuilder);
    }

    protected QueryBuilder buildQueryBuilder() {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        QueryBuilder term = new TermQueryBuilderFactory(getFieldName(), fieldValue).instance();
        boolQueryBuilder.must(term);
        return buildFinalQuery(boolQueryBuilder);
    }

    protected String getFieldName() {
        String fieldName = this.fieldName;
        if (ESObjectConstants.DOC_ID.equals(fieldName) || containsGuid(fieldName)) {
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
        String objectName = Util.capitalCamelCaseToLowerCamelCase(getObjectName());
        return String.join(DBConstants.ALIAS_DELIMITER, BaseGenerator.DSM_OBJECT, objectName, fieldName);
    }

    private String getObjectName() {
        String classSimpleName;
        if (source instanceof List) {
            classSimpleName = ((List)source).get(0).getClass().getSimpleName();
        } else {
            classSimpleName = source.getClass().getSimpleName();
        }
        return classSimpleName;
    }

    public void export() {
        upsertPainless.export();
    }

    protected boolean containsGuid(String fieldName) {
        return Arrays.asList(fieldName.split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR)).contains(ESObjectConstants.GUID);
    }
}
