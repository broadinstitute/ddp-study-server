package org.broadinstitute.dsm.model.elastic.export.generate;

import static org.junit.Assert.assertEquals;

import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.statics.DBConstants;
import org.junit.Test;

public class PropertyInfoTest {

    @Test
    public void getPrimaryKey() {
        PropertyInfo propertyInfo = new PropertyInfo(CohortTag.class, true);
        String cohortTagPk = propertyInfo.getPrimaryKeyAsCamelCase();
        assertEquals(CamelCaseConverter.of(DBConstants.COHORT_TAG_PK).convert(), cohortTagPk);
    }
}
