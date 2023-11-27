package org.broadinstitute.ddp.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.broadinstitute.ddp.json.AnswerSubmission;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class GsonBigDecimalDeserializationTest {
    @Test
    public void testBigDecimalDeserialization() {
        final String s = "{\"stableId\":\"test\",\"answerGuid\":\"test\",\"value\":{\"unscaledValue\":10,\"scale\":2}}";

        final AnswerSubmission answerSubmission = new Gson().fromJson(s, AnswerSubmission.class);
        final JsonObject value = answerSubmission.getValue().getAsJsonObject();
        final BigDecimal decimalValue = new BigDecimal(value.get("unscaledValue").getAsBigInteger(),
                value.get("scale").getAsInt());

        assertEquals("test", answerSubmission.getAnswerGuid());
        assertEquals(0, BigDecimal.valueOf(0.1).compareTo(decimalValue));
    }

    @Test
    public void testBigDecimalDirectDeserialization() {
        final String s = "{\"stableId\":\"test\",\"answerGuid\":\"test\",\"value\":654321.123456}";

        final AnswerSubmission answerSubmission = new Gson().fromJson(s, AnswerSubmission.class);
        final BigDecimal decimalValue = answerSubmission.getValue().getAsBigDecimal();

        assertEquals("test", answerSubmission.getAnswerGuid());
        assertEquals(0, BigDecimal.valueOf(654321.123456).compareTo(decimalValue));
    }
}
