package org.broadinstitute.ddp.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.broadinstitute.ddp.json.AnswerSubmission;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.util.TestUtil.readJsonObjectFromFile;
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
}
