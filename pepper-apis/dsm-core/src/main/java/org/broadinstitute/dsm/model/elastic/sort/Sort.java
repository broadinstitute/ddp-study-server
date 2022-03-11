package org.broadinstitute.dsm.model.elastic.sort;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.TypeParser;
import org.broadinstitute.dsm.model.elastic.mapping.TypeExtractor;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.search.sort.SortOrder;

public class Sort {

    SortBy sortBy;
    TypeExtractor<Map<String, String>> typeExtractor;

    Sort(SortBy sortBy, TypeExtractor<Map<String, String>> typeExtractor) {
        this.typeExtractor = typeExtractor;
        this.sortBy = sortBy;
    }

    public static Sort of(SortBy sortBy, TypeExtractor<Map<String, String>> typeExtractor) {
        Type type = Type.valueOf(sortBy.getType());
        switch (type) {
            case ACTIVITY:
                return new ActivityTypeSort(sortBy, typeExtractor);
            case ADDITIONALVALUE:
                return new AdditionalValueTypeSort(sortBy, typeExtractor);
            case JSONARRAY:
                return new JsonArrayTypeSort(sortBy, typeExtractor);
            default:
                return new Sort(sortBy, typeExtractor);
        }
    }

    boolean isNestedSort() {
        return Alias.of(sortBy).isCollection();
    }

    String buildFieldName() {

        Type type = Type.valueOf(sortBy.getType());

        String outerProperty = handleOuterPropertySpecialCase();
        String innerProperty = handleInnerPropertySpecialCase();

        return buildPath(getAliasValue(Alias.of(sortBy)), outerProperty, innerProperty, getKeywordIfText(type));
    }

    String handleOuterPropertySpecialCase() {
        Alias alias = Alias.of(sortBy);
        if (alias.equals(Alias.PARTICIPANTDATA)) {
            return ESObjectConstants.DYNAMIC_FIELDS;
        }
        return sortBy.getOuterProperty();
    }

    public String handleInnerPropertySpecialCase() {
        if (Alias.ACTIVITIES == Alias.of(sortBy)) {
            return sortBy.getInnerProperty();
        }
        return Util.underscoresToCamelCase(sortBy.getInnerProperty());
    }

    private String buildPath(String... args) {
        return Stream.of(args).filter(StringUtils::isNotBlank).collect(Collectors.joining(DBConstants.ALIAS_DELIMITER));
    }

    String getAliasValue(Alias alias) {
        return alias.getValue();
    }

    protected String getKeywordIfText(Type innerType) {
        if (isTextContent(innerType) && isFieldTextType()) {
            return buildPath(TypeParser.KEYWORD);
        }
        return StringUtils.EMPTY;
    }

    private boolean isTextContent(Type innerType) {
        return innerType == Type.TEXT || innerType == Type.TEXTAREA || innerType == Type.RADIO || innerType == Type.OPTIONS
                || innerType == Type.ACTIVITY;
    }

    private boolean isFieldTextType() {
        this.typeExtractor.setFields(
                buildPath(getAliasValue(Alias.of(sortBy)), handleOuterPropertySpecialCase(), handleInnerPropertySpecialCase()));
        return TypeParser.TEXT.equals(typeExtractor.extract().get(handleInnerPropertySpecialCase()));
    }

    String buildNestedPath() {
        if (isNestedSort()) {
            Type type = Type.valueOf(sortBy.getType());
            Alias alias = Alias.of(sortBy);
            if (isDoubleNested(type, alias)) {
                return buildPath(getAliasValue(Alias.of(sortBy)), sortBy.getOuterProperty());
            }
            return buildPath(getAliasValue(Alias.of(sortBy)));
        }
        throw new UnsupportedOperationException("Building nested path on non-nested objects is unsupported");
    }

    private boolean isDoubleNested(Type type, Alias alias) {
        return type == Type.JSONARRAY || (alias == Alias.ACTIVITIES && ElasticSearchUtil.QUESTIONS_ANSWER.equals(
                sortBy.getOuterProperty()));
    }

    public SortOrder getOrder() {
        return SortOrder.valueOf(sortBy.getOrder().toUpperCase());
    }

    public Alias getAlias() {
        return Alias.of(sortBy);
    }

    public String getRawAlias() {
        return sortBy.getTableAlias();
    }

    public String[] getActivityVersions() {
        return sortBy.getActivityVersions();
    }
}
