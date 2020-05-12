package org.broadinstitute.ddp.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GoogleRecaptchaClientTest {
    @Test
    public void testReachingGoogle() {
        GoogleRecaptchaVerifyResponse verifyResponse = new GoogleRecaptchaVerifyClient("nothing").verifyRecaptchaResponse("1234565");
        assertNotNull(verifyResponse);
        assertFalse(verifyResponse.isSuccess());
        assertTrue(verifyResponse.getErrorCodes().size() > 0 );
    }
}
