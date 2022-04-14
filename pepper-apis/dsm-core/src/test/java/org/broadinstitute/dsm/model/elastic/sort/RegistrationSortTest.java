package org.broadinstitute.dsm.model.elastic.sort;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RegistrationSortTest {

    @Test
    public void buildRegistrationSortWithTypeOptions() {
        SortBy sortBy = new SortBy.Builder()
                .withType("OPTIONS")
                .withOrder("ASC")
                .withInnerProperty("DATSTAT_GENDER")
                .withOuterProperty("AT_PARTICIPANT_INFO")
                .withTableAlias("REGISTRATION")
                .build();
        Sort sort = Sort.of(sortBy, SortTest.mockFieldTypeExractorByFieldAndType("REGISTRATION_GENDER", "text"));
        String nestedPath = sort.buildNestedPath();
        assertEquals("activities.questionsAnswers", nestedPath);
    }
}
