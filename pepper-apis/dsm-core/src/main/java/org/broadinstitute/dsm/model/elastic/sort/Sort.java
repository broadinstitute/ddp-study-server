package org.broadinstitute.dsm.model.elastic.sort;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.search.sort.SortOrder;

public class Sort {

    SortBy sortBy;
    TypeExtractor<Map<String, String>> typeExtractor;
    private static final String LOWER_CASE_SORT = "lower_case_sort";

    private Alias alias;

    Sort(SortBy sortBy,
                TypeExtractor<Map<String, String>> typeExtractor) {
        this.typeExtractor = typeExtractor;
        this.sortBy = sortBy;
        this.alias = Alias.of(sortBy);
    }

    public static Sort of(SortBy sortBy, TypeExtractor<Map<String, String>> typeExtractor) {
        Type type = Type.of(sortBy.getType());
        switch (type) {
            case ACTIVITY:
                return new ActivityTypeSort(sortBy, typeExtractor);
            case ADDITIONALVALUE:
                return new AdditionalValueTypeSort(sortBy, typeExtractor);
            case JSONARRAY:
                return new JsonArrayTypeSort(sortBy, typeExtractor);
            default:
                if (Alias.of(sortBy) == Alias.REGISTRATION) {
                    return new RegistrationSort(sortBy, typeExtractor);
                }
                return new Sort(sortBy, typeExtractor);
        }
    }
    
    boolean isNestedSort() {
        return getAlias().isCollection();
    }

    String buildFieldName() {

        Type type = Type.of(sortBy.getType());

        String outerProperty = handleOuterPropertySpecialCase();
        String innerProperty = handleInnerPropertySpecialCase();

        return buildPath(getAliasValue(getAlias()), outerProperty, innerProperty, getKeywordIfText(type));
    }

    String handleOuterPropertySpecialCase() {
        if (getAlias().equals(Alias.PARTICIPANTDATA)) {
            return ESObjectConstants.DYNAMIC_FIELDS;
        }
        return sortBy.getOuterProperty();
    }

    public String handleInnerPropertySpecialCase() {
        if (Alias.ACTIVITIES == getAlias() || Alias.REGISTRATION == getAlias()) {
            return sortBy.getInnerProperty();
        }
        return CamelCaseConverter.of(sortBy.getInnerProperty()).convert();
    }

    private String buildPath(String... args) {
        return Stream.of(args)
                .filter(StringUtils::isNotBlank)
                .flatMap(pathPart -> Stream.of(pathPart.split(ElasticSearchUtil.ESCAPE_CHARACTER_DOT_SEPARATOR)))
                .distinct()
                .collect(Collectors.joining(DBConstants.ALIAS_DELIMITER));
    }

    String getAliasValue(Alias alias) {
        return alias.getValue();
    }

    protected String getKeywordIfText(Type innerType) {
        if (innerType.isTextContent() && isFieldTextType()) {
            return buildPath(LOWER_CASE_SORT);
        }
        return StringUtils.EMPTY;
    }

    private boolean isFieldTextType() {
        this.typeExtractor.setFields(buildPath(getAliasValue(getAlias()), handleOuterPropertySpecialCase(),
                handleInnerPropertySpecialCase()));
        return TypeParser.TEXT.equals(typeExtractor.extract().get(handleInnerPropertySpecialCase()));
    }

    protected String buildQuestionsAnswersPath() {
        return String.join(DBConstants.ALIAS_DELIMITER, getAlias().getValue(), ElasticSearchUtil.QUESTIONS_ANSWER);
    }

    String buildNestedPath() {
        if (isNestedSort()) {
            Type type = Type.of(sortBy.getType());
            if (isDoubleNested(type)) {
                return buildPath(getAliasValue(getAlias()), sortBy.getOuterProperty());
            }
            return buildPath(getAliasValue(getAlias()));
        }
        throw new UnsupportedOperationException("Building nested path on non-nested objects is unsupported");
    }

    private boolean isDoubleNested(Type type) {
        return type == Type.JSONARRAY || (getAlias() == Alias.ACTIVITIES
                && ElasticSearchUtil.QUESTIONS_ANSWER.equals(sortBy.getOuterProperty()));
    }

    public SortOrder getOrder() {
        return SortOrder.valueOf(sortBy.getOrder().toUpperCase());
    }

    public Alias getAlias() {
        return alias;
    }

    public String getRawAlias() {
        return sortBy.getTableAlias();
    }

    public String[] getActivityVersions() {
        return sortBy.getActivityVersions();
    }
}
