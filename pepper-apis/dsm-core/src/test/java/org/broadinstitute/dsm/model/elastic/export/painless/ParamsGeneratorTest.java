package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.birch.DSMTestResult;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
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
    public void generateKitRequest() {

        DSMTestResult[] array = new DSMTestResult[] {new DSMTestResult("Negative", "2012-05-02", true)};
        String finalArray = ObjectMapperSingleton.writeValueAsString(array);

        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setTestResult(finalArray);

        ParamsGenerator paramsGenerator = new ParamsGenerator(kitRequestShipping, null);
        Map<String, Object> actual = paramsGenerator.generate();

        Map<String, Object> innerMap =
                new LinkedHashMap<>(Map.of("timeCompleted", "2012-05-02", "result", "Negative", "isCorrected", true));
        List<Map<String, Object>> innerMapInList = new ArrayList<>(List.of(innerMap));
        Map<String, Object> fullInnerMap = new HashMap<>(Map.of(ESObjectConstants.KIT_TEST_RESULT, innerMapInList));
        Map<String, Map<String, Object>> kitRequestShippingMap =
                new HashMap<>(Map.of(ESObjectConstants.KIT_REQUEST_SHIPPING, fullInnerMap));
        Map<String, Object> expected = new HashMap<>(Map.of(ESObjectConstants.DSM, kitRequestShippingMap));

        Assert.assertEquals(expected, actual);

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
