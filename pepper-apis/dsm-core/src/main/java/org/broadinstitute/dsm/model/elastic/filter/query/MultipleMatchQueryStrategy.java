package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class MultipleMatchQueryStrategy extends BaseQueryStrategy {
    public static String FIELD_NAME_ANSWER = "activities.questionsAnswers.answer";
    public static String FIELD_NAME_GROUPED_OPTIONS = "activities.questionsAnswers.groupedOptions.";

    @Override
    protected QueryBuilder getMainQueryBuilderFromChild(BaseQueryBuilder baseQueryBuilder) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        Object[] values = baseQueryBuilder.payload.getValues();
        for (Object value : values) {
            String[] fieldNameAndValue =getCorrectFieldNameAndValues(baseQueryBuilder.payload.getFieldName(), (String) value);
            boolQueryBuilder.should(new MatchQueryBuilder(fieldNameAndValue[0], fieldNameAndValue[1]));
        }
        return boolQueryBuilder;
    }

    private String[] getCorrectFieldNameAndValues(String fieldName, String value){
        String[] fieldNameAndValue = new String[2];
        if (!FIELD_NAME_ANSWER.equals(fieldName) || !value.contains(".")){
            fieldNameAndValue[0] = fieldName;
            fieldNameAndValue[1] = value;
            return fieldNameAndValue;
        }
        String[] activityGroupPath = value.split("\\.");
        String path = trimSingleQuote(activityGroupPath[0]);
        fieldNameAndValue[0] = FIELD_NAME_GROUPED_OPTIONS.concat(path);
        fieldNameAndValue[1] = trimSingleQuote(activityGroupPath[1]);
        return fieldNameAndValue;


    }

    private String trimSingleQuote(String s){
        return s.replaceAll("'","");
    }

}
