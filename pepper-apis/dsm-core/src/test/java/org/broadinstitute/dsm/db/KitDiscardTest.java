package org.broadinstitute.dsm.db;

import com.google.gson.Gson;
import org.broadinstitute.lddp.util.GsonResponseTransformer;
import org.junit.Assert;
import org.junit.Test;

public class KitDiscardTest {

    @Test
    public void testKitDiscardSerialization() {
        KitDiscard kitDiscard = KitDiscard.builder()
                .kitRequestId("kitRequestId")
                .kitType("kitType")
                .kitLabel("kitLabel")
                .exitDate(System.currentTimeMillis())
                .path("path/to/kit")
                .build();
        try {
            GsonResponseTransformer transformer = new GsonResponseTransformer();
            String serialized = transformer.render(kitDiscard);
            KitDiscard deserialized = new Gson().fromJson(serialized, KitDiscard.class);
            Assert.assertEquals(kitDiscard, deserialized);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Failed to serialize KitDiscard object");
        }
    }
}
