package org.broadinstitute.dsm.model.elastic.export.parse.abstraction.transformer.mapping;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.converters.split.SpaceSplittingStrategy;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;

public abstract class MedicalRecordAbstractionMappingGenerator {

    public static final String SINGLE_ANSWER = "singleAnswer";
    public static final String OTHER         = "other";
    public static final String VALUES        = "values";
    public static final String EST           = "est";
    public static final String TYPE          = "type";

    protected final BaseParser baseParser;
    protected final CamelCaseConverter camelCaseConverter;

    public MedicalRecordAbstractionMappingGenerator() {
        this.baseParser = new TypeParser();
        this.camelCaseConverter = CamelCaseConverter.of();
        this.camelCaseConverter.setSplittingStrategy(new SpaceSplittingStrategy());
    }

    public abstract Map<String, Object> toMap(String fieldName);

}
