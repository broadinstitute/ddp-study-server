package org.broadinstitute.dsm.model.elastic.sort;

import static org.junit.Assert.assertEquals;

import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.junit.Before;
import org.junit.Test;

public class ActivityTypeSortTest {

    @Before
    public void setUp() {
        FieldSettingsDao.setInstance(new MockFieldSettingsDao());
    }


    @Test
    public void buildActivityTypeFieldName() {
        SortBy sortBy = new SortBy.Builder().withType("ACTIVITY").withOrder("ASC").withInnerProperty("DATSTAT_GENDER")
                .withOuterProperty("AT_PARTICIPANT_INFO").withTableAlias("participantData").build();
        Sort sort = new ActivityTypeSort(sortBy, SortTest.mockFieldTypeExractorByFieldAndType("REGISTRATION_GENDER", "text"));
        String fieldName = sort.buildFieldName();
        assertEquals("activities.questionsAnswers.REGISTRATION_GENDER.lower_case_sort", fieldName);
    }

    @Test
    public void buildActivityTypeNestedPath() {
        SortBy sortBy = new SortBy.Builder().withType("ACTIVITY").withOrder("ASC").withInnerProperty("DATSTAT_GENDER")
                .withOuterProperty("AT_PARTICIPANT_INFO").withTableAlias("participantData").build();
        Sort sort = new ActivityTypeSort(sortBy, SortTest.mockFieldTypeExractorByFieldAndType("REGISTRATION_GENDER", "text"));
        String nestedPath = sort.buildNestedPath();
        assertEquals("activities.questionsAnswers", nestedPath);
    }
}
