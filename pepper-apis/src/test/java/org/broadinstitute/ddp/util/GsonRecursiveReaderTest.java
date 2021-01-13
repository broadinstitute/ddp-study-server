package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.util.TestUtil.readJSONFromFile;
import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;


import com.google.gson.JsonElement;
import org.junit.Test;

public class GsonRecursiveReaderTest {

    private static final String TEST_JSON = "src/test/resources/json_example.json";

    /**
     * Verify that found 1st encountered value (by name).
     */
    @Test
    public void testJsonRecursiveReading() throws FileNotFoundException {
        final Map<String, JsonElement> values =
                GsonRecursiveReader.readValues(readJSONFromFile(TEST_JSON), List.of("title", "name", "hOffset"));
        assertEquals("Sample Component", values.get("title").getAsString());
        assertEquals("win1", values.get("name").getAsString());
        assertEquals("150", values.get("hOffset").getAsString());
    }
}
