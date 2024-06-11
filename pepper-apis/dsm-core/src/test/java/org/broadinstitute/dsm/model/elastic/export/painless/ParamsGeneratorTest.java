package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.junit.Assert;
import org.junit.Test;

public class ParamsGeneratorTest {

    @Test
    public void generate() {
        KitRequestShipping kitRequestShipping = new KitRequestShipping(1, 2L, "easyPostIdValue", "easyPostAddressIdValue", true, "msg");
        ParamsGenerator paramsGenerator = new ParamsGenerator(kitRequestShipping, StringUtils.EMPTY);
        Map<String, Object> paramsMap = paramsGenerator.generate();
        Map<String, Object> dsm = (Map<String, Object>) paramsMap.get("dsm");
        Map<String, Object> kitRequestShippingObj = (Map<String, Object>) dsm.get("kitRequestShipping");
        Assert.assertEquals(1, kitRequestShippingObj.get("dsmKitRequestId"));
        Assert.assertEquals(2L, kitRequestShippingObj.get("dsmKitId"));
        Assert.assertEquals("easyPostIdValue", kitRequestShippingObj.get("easypostToId"));
        Assert.assertEquals(true, kitRequestShippingObj.get("error"));
        Assert.assertEquals("msg", kitRequestShippingObj.get("message"));
    }

    @Test
    public void generateDeleteCohortTagParams() {
        CohortTag cohortTag = new CohortTag();
        cohortTag.setCohortTagId(12);
        Generator paramsGenerator = new ParamsGenerator(cohortTag, StringUtils.EMPTY);
        Map<String, Object> deleteCohortTagParams = paramsGenerator.generate();
        Map<String, Object> dsm = (Map<String, Object>) deleteCohortTagParams.get(ESObjectConstants.DSM);
        Map<String, Object> cohortTagMap = (Map<String, Object>) dsm.get(ESObjectConstants.COHORT_TAG);
        Assert.assertEquals(1, cohortTagMap.size());
        Assert.assertEquals(12, cohortTagMap.get(ESObjectConstants.DSM_COHORT_TAG_ID));
    }

}
