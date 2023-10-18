package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.broadinstitute.dsm.util.UserUtil;
import org.junit.Assert;
import org.junit.Test;
import spark.QueryParamsMap;

public class RouteUtilTest {

    @Test
    public void getUserIdTest() {
        String jsonQueryParams = "{\"queryMap\":{\"userId\":{\"queryMap\":{},\"values\":[\"4\"]},\"realm\":{\"queryMap\":{},"
                + "\"values\":[\"RGP\"]}}}";
        QueryParamsMap queryParams = new Gson().fromJson(jsonQueryParams, QueryParamsMap.class);
        Assert.assertEquals(Integer.toString(4), queryParams.value("userId"));
        String userId = UserUtil.getUserId(queryParams);
        Assert.assertEquals("4", userId);

        String payload = "{\"facility\": null,\"policyId\": \"12\",\"userId\": \"4\"}";
        JsonObject jsonObject = JsonParser.parseString(payload).getAsJsonObject();
        // wrong way
        String user = String.valueOf(jsonObject.get("userId"));
        Assert.assertEquals("\"4\"", user);
        // correct way
        user = jsonObject.get("userId").getAsString();
        Assert.assertEquals("4", user);
    }
}
