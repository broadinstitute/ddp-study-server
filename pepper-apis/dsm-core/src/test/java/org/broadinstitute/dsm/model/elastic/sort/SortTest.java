package org.broadinstitute.dsm.model.elastic.sort;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.broadinstitute.dsm.model.elastic.MockFieldTypeExtractor;
import org.junit.Test;


public class SortTest {

    @Test
    public void buildJsonArrayFieldName() {

        SortBy sortBy = new SortBy.Builder()
                .withType("JSONARRAY")
                .withAdditionalType("CHECKBOX")
                .withOrder("ASC")
                .withOuterProperty("testResult")
                .withInnerProperty("isCorrected")
                .withTableAlias("k")
                .build();

        Sort sort = Sort.of(sortBy, mockFieldTypeExractorByFieldAndType("isCorrected", ""));
        String fieldName = sort.buildFieldName();

        assertEquals("dsm.kitRequestShipping.testResult.isCorrected", fieldName);
    }

    @Test
    public void buildJsonArrayFieldNameWithKeyword() {

        SortBy sortBy = new SortBy.Builder()
                .withType("JSONARRAY")
                .withAdditionalType("TEXT")
                .withOrder("ASC")
                .withOuterProperty("testResult")
                .withInnerProperty("isCorrected")
                .withTableAlias("k")
                .build();

        Sort sort = Sort.of(sortBy, mockFieldTypeExractorByFieldAndType("isCorrected", "text"));
        String fieldName = sort.buildFieldName();

        assertEquals("dsm.kitRequestShipping.testResult.isCorrected.lower_case_sort", fieldName);
    }

    @Test
    public void buildAdditionalValueFieldName() {
        SortBy sortBy = new SortBy.Builder()
                .withType("ADDITIONALVALUE")
                .withAdditionalType("TEXT")
                .withOrder("ASC")
                .withInnerProperty("hello")
                .withTableAlias("m")
                .build();

        Sort sort = Sort.of(sortBy, mockFieldTypeExractorByFieldAndType("hello", "text"));
        String fieldName = sort.buildFieldName();
        assertEquals("dsm.medicalRecord.dynamicFields.hello.lower_case_sort", fieldName);
    }

    @Test
    public void buildSingleFieldName() {
        SortBy sortBy = new SortBy.Builder()
                .withType("TEXT")
                .withOrder("ASC")
                .withInnerProperty("notes")
                .withTableAlias("m")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("notes", "text"));
        String fieldName = sort.buildFieldName();
        assertEquals("dsm.medicalRecord.notes.lower_case_sort", fieldName);
    }



    @Test
    public void buildOneLevelNestedPath() {
        SortBy sortBy = new SortBy.Builder()
                .withType("TEXT")
                .withOrder("ASC")
                .withInnerProperty("notes")
                .withTableAlias("m")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("notes", "text"));
        String nestedPath = sort.buildNestedPath();
        assertEquals("dsm.medicalRecord", nestedPath);
    }

    @Test
    public void buildTwoLevelNestedPath() {
        SortBy sortBy = new SortBy.Builder()
                .withType("JSONARRAY")
                .withOrder("ASC")
                .withInnerProperty("result")
                .withTableAlias("k")
                .withOuterProperty("testResult")
                .build();
        Sort sort = Sort.of(sortBy, mockFieldTypeExractorByFieldAndType("result", "text"));
        String nestedPath = sort.buildNestedPath();
        assertEquals("dsm.kitRequestShipping.testResult", nestedPath);
    }

    @Test
    public void handleOuterPropertySpecialCase() {
        SortBy sortBy = new SortBy.Builder()
                .withType("RADIO")
                .withOrder("ASC")
                .withInnerProperty("REGISTRATION_STATUS")
                .withTableAlias("participantData")
                .withOuterProperty("AT_GROUP_MISCELLANEOUS")
                .build();
        Sort sort = Sort.of(sortBy, mockFieldTypeExractorByFieldAndType("REGISTRATION_STATUS", ""));
        String outerProperty = sort.handleOuterPropertySpecialCase();
        assertEquals("dynamicFields", outerProperty);
    }

    @Test
    public void buildParticipantDataFieldName() {
        SortBy sortBy = new SortBy.Builder()
                .withType("RADIO")
                .withOrder("ASC")
                .withInnerProperty("REGISTRATION_STATUS")
                .withTableAlias("participantData")
                .withOuterProperty("AT_GROUP_MISCELLANEOUS")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("registrationStatus", "text"));
        String outerProperty = sort.buildFieldName();
        assertEquals("dsm.participantData.dynamicFields.registrationStatus.lower_case_sort", outerProperty);
    }

    @Test
    public void buildNonDsmFieldName() {
        SortBy sortBy = new SortBy.Builder()
                .withType("TEXT")
                .withOrder("ASC")
                .withInnerProperty("country")
                .withTableAlias("data")
                .withOuterProperty("address")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("country", "text"));
        String outerProperty = sort.buildFieldName();
        assertEquals("address.country.lower_case_sort", outerProperty);
    }

    @Test
    public void buildNonDsmStatusFieldName() {
        SortBy sortBy = new SortBy.Builder()
                .withType("OPTIONS")
                .withOrder("ASC")
                .withInnerProperty("status")
                .withTableAlias("data")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("status", "text"));
        String outerProperty = sort.buildFieldName();
        assertEquals("status.lower_case_sort", outerProperty);
    }

    @Test
    public void buildNonDsmProfileCreatedAtFieldName() {
        SortBy sortBy = new SortBy.Builder()
                .withType("DATE")
                .withOrder("ASC")
                .withInnerProperty("createdAt")
                .withTableAlias("data")
                .withOuterProperty("profile")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("createdAt", ""));
        String outerProperty = sort.buildFieldName();
        assertEquals("profile.createdAt", outerProperty);
    }

    @Test
    public void buildNonDsmInvitationsAcceptedAtFieldName() {
        SortBy sortBy = new SortBy.Builder()
                .withType("DATE")
                .withOrder("ASC")
                .withInnerProperty("acceptedAt")
                .withTableAlias("invitations")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("acceptedAt", ""));
        String outerProperty = sort.buildFieldName();
        assertEquals("invitations.acceptedAt", outerProperty);
    }

    @Test
    public void buildNonDsmProxytFieldName() {
        SortBy sortBy = new SortBy.Builder()
                .withType("TEXT")
                .withOrder("ASC")
                .withInnerProperty("email")
                .withTableAlias("proxy")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("email", "text"));
        String outerProperty = sort.buildFieldName();
        assertEquals("profile.email.lower_case_sort", outerProperty);
    }

    @Test
    public void handleInnerPropertySpecialCase() {
        SortBy sortBy = new SortBy.Builder()
                .withType("TEXT")
                .withOrder("ASC")
                .withInnerProperty("YEARS")
                .withOuterProperty("questionsAnswers")
                .withTableAlias("MEDICAL_HISTORY")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("YEARS", "text"));
        String innerProperty = sort.handleInnerPropertySpecialCase();
        assertEquals("YEARS", innerProperty);
    }

    @Test
    public void buildQuestionsAnswersFieldName() {
        SortBy sortBy = new SortBy.Builder()
                .withType("TEXT")
                .withOrder("ASC")
                .withInnerProperty("YEARS")
                .withOuterProperty("questionsAnswers")
                .withTableAlias("MEDICAL_HISTORY")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("YEARS", "text"));
        String outerProperty = sort.buildFieldName();
        assertEquals("activities.questionsAnswers.YEARS.lower_case_sort", outerProperty);
    }

    @Test
    public void buildQuestionsAnswersFieldNameForNumberType() {
        SortBy sortBy = new SortBy.Builder()
                .withType("NUMBER")
                .withOrder("ASC")
                .withInnerProperty("YEARS")
                .withOuterProperty("questionsAnswers")
                .withTableAlias("MEDICAL_HISTORY")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("YEARS", ""));
        String outerProperty = sort.buildFieldName();
        assertEquals("activities.questionsAnswers.YEARS", outerProperty);
    }

    @Test
    public void buildNestedPath() {
        SortBy sortBy = new SortBy.Builder()
                .withType("NUMBER")
                .withOrder("ASC")
                .withInnerProperty("YEARS")
                .withOuterProperty("questionsAnswers")
                .withTableAlias("MEDICAL_HISTORY")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("YEARS", ""));
        String actual = sort.buildNestedPath();
        assertEquals("activities.questionsAnswers", actual);
    }

    @Test
    public void buildPathIfSameKeys() {
        SortBy sortBy = new SortBy.Builder()
                .withType("TEXT")
                .withOrder("ASC")
                .withInnerProperty("YEARS")
                .withOuterProperty("questionsAnswers")
                .withTableAlias("REGISTRATION")
                .build();
        Sort sort = new Sort(sortBy, mockFieldTypeExractorByFieldAndType("YEARS", ""));
        String actual = sort.buildFieldName();
        assertEquals("activities.questionsAnswers.YEARS", actual);
    }

    static MockFieldTypeExtractor mockFieldTypeExractorByFieldAndType(String fieldName, String fieldType) {
        return new MockFieldTypeExtractor() {
            @Override
            public Map<String, String> extract() {
                return Map.of(fieldName, fieldType);
            }
        };
    }
}
