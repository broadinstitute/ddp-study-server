package org.broadinstitute.dsm.model.elastic.converters;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Map;

import org.broadinstitute.dsm.model.FollowUp;
import org.junit.Test;

public class FollowUpsConverterTest {

    @Test
    public void convert() {
        FollowUp followUp = new FollowUp("2019-04-18", "2019-04-18", "2019-04-18", "2019-04-19");
        FollowUp followUp2 = new FollowUp("2020-04-18", "2020-04-18", "2020-04-18", "2020-04-19");

        FollowUpsConverter followUpsConverter = new FollowUpsConverter();
        followUpsConverter.fieldName = "follow_ups";
        followUpsConverter.fieldValue = Arrays.asList(followUp, followUp2);

        Map<String, Object> actualFollowUps = followUpsConverter.convert();

        String followUpsJson =
                "[{\"fRequest1\":\"2019-04-18\",\"fRequest2\":\"2019-04-18\",\"fRequest3\":\"2019-04-18\",\"fReceived\":\"2019-04-19\"},"
                + "{\"fRequest1\":\"2020-04-18\",\"fRequest2\":\"2020-04-18\",\"fRequest3\":\"2020-04-18\",\"fReceived\":\"2020-04-19\"}]";

        Map<String, String> expectedFollowUps = Map.of("followUps", followUpsJson);

        assertEquals(expectedFollowUps, actualFollowUps);
    }
}
