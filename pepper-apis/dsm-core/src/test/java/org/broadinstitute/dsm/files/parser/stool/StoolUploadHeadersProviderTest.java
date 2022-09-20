
package org.broadinstitute.dsm.files.parser.stool;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class StoolUploadHeadersProviderTest {

    @Test
    public void provideHeaders() {
        var provider = new StoolUploadHeadersProvider();
        var actual   = provider.provideHeaders();
        Assert.assertEquals(List.of("participantId", "mfBarcode", "receiveDate"), actual);
    }
}
