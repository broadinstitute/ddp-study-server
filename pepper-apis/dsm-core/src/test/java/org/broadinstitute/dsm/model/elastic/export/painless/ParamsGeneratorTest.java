package org.broadinstitute.dsm.model.elastic.export.painless;

import com.google.gson.Gson;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.birch.DSMTestResult;
import org.broadinstitute.dsm.model.birch.TestBostonResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class ParamsGeneratorTest {

    @Test
    public void generate() {
        KitRequestShipping kitRequestShipping = new KitRequestShipping(1L, 2L, "easyPostIdValue", "easyPostAddressIdValue", true, "msg");
        ParamsGenerator paramsGenerator = new ParamsGenerator(kitRequestShipping, "");
        Map<String, Object> paramsMap = paramsGenerator.generate();
        Map <String, Object> dsm = (Map <String, Object>) paramsMap.get("dsm");
        Map <String, Object> kitRequestShippingObj = (Map <String, Object>) dsm.get("kitRequestShipping");
        Assert.assertEquals(1L, kitRequestShippingObj.get("dsmKitRequestId"));
        Assert.assertEquals(2L, kitRequestShippingObj.get("dsmKitId"));
        Assert.assertEquals("easyPostIdValue", kitRequestShippingObj.get("easypostToId"));
        Assert.assertEquals(true, kitRequestShippingObj.get("error"));
        Assert.assertEquals("msg", kitRequestShippingObj.get("message"));
    }

    @Test
    public void generateKitRequest() {

        DSMTestResult[] array = new DSMTestResult[] { new DSMTestResult("Negative", "2012-05-02", true) };
        String finalArray = ObjectMapperSingleton.writeValueAsString(array);

        KitRequestShipping kitRequestShipping = new KitRequestShipping();
        kitRequestShipping.setTestResult(finalArray);

        ParamsGenerator paramsGenerator = new ParamsGenerator(kitRequestShipping, null);
        Map<String, Object> actual = paramsGenerator.generate();

        Map<String, Object> expected = Map.of(ESObjectConstants.DSM, Map.of(ESObjectConstants.KIT_REQUEST_SHIPPING,
                Map.of(ESObjectConstants.KIT_TEST_RESULT, List.of(
                        Map.of("result", "Negative", "timeCompleted", "2012-05-02", "isCorrected", true)))
        ));

        Assert.assertEquals(expected, actual);

    }

}