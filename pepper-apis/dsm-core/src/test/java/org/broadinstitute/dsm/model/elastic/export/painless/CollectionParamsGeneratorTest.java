package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.junit.Assert;
import org.junit.Test;

public class CollectionParamsGeneratorTest {


    @Test
    public void generate() {

        List<CohortTag> cohortTags = Arrays.asList(
                new CohortTag(12, "testTag", "TEST01", 13),
                new CohortTag(15, "testTag2", "TEST02", 13)
        );
        Generator collectionParamsGenerator = new CollectionParamsGenerator<>(cohortTags, "angio");
        Map<String, Object> collectionParams = collectionParamsGenerator.generate();

        Map<String, Object> dsm = (Map<String, Object>)collectionParams.get(ESObjectConstants.DSM);
        try {
            List<Map<String, Object>> actualCohortTags = (List<Map<String, Object>>) dsm.get(ESObjectConstants.COHORT_TAG);
            Assert.assertEquals(15, actualCohortTags.get(1).get(ESObjectConstants.DSM_COHORT_TAG_ID));
        } catch (ClassCastException cce) {
            Assert.fail();
        }

    }


}
