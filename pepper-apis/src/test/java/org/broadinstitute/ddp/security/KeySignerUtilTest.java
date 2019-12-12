package org.broadinstitute.ddp.security;

import org.junit.Assert;
import org.junit.Test;

public class KeySignerUtilTest {

    public static final String TESTING_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\n"
            + "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA7XODEvBToyqOYHKFbhQQ\n"
            + "9YTCXFoEjVjcFoVeI60KaQdcxGqRt+h6gkxeQHzVKajPiDiZSaWTtEcqdSCsE4Ba\n"
            + "kYDodxwnaiyuDWZYkoGl4XhJ6w+b7BG0HX7IB8ZtoHvMDs/jlaCHnYTn25fRK/4u\n"
            + "2Fnt5mDwMEkdRkMbyP0dFfUXQ1P2U26Zr/xCnWfezTONb3jbtB7FTZXfKdhcPStr\n"
            + "FwI3Qs2EjuAFMyQkaa/SrNpXpsf+EJtpm2+TRd2IjoulTWd888Ju2uIolSBXFBZr\n"
            + "95HRtq+IUpSy43CjTpIvKX/OyK3k3neMJNUQKLT3a6+pCXRBjrm1HftW2x24/mJ1\n"
            + "7QIDAQAB\n"
            + "-----END PUBLIC KEY-----";
    public static final String TESTING_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----\n"
            +
            "MIIEpQIBAAKCAQEA7XODEvBToyqOYHKFbhQQ9YTCXFoEjVjcFoVeI60KaQdcxGqR\n"
            +
            "t+h6gkxeQHzVKajPiDiZSaWTtEcqdSCsE4BakYDodxwnaiyuDWZYkoGl4XhJ6w+b\n"
            + "7BG0HX7IB8ZtoHvMDs/jlaCHnYTn25fRK/4u2Fnt5mDwMEkdRkMbyP0dFfUXQ1P2\n"
            + "U26Zr/xCnWfezTONb3jbtB7FTZXfKdhcPStrFwI3Qs2EjuAFMyQkaa/SrNpXpsf+\n"
            + "EJtpm2+TRd2IjoulTWd888Ju2uIolSBXFBZr95HRtq+IUpSy43CjTpIvKX/OyK3k\n"
            + "3neMJNUQKLT3a6+pCXRBjrm1HftW2x24/mJ17QIDAQABAoIBAQDBjKKO0W6bVZjw\n"
            + "bOjuLVUVi72R4Z5MSN49TUDK+8W8js/DGsrkiY8ynmVFU3u9lWh0tQ3dxiV7kXa+\n"
            + "On+I9drdN6JFVKGcHgdRzNbttNgtzQsTm4mRB201jZ4nGEtLwR04BaSQ1mU0tJz5\n"
            + "lepeXbZ5i/QrEWE3OqGuIA2J6yfGOIstrOJS/ubLfq+sj5688OcQr9AUgtEIaj1p\n"
            + "aBYCnARhoTjAh+9Iv4mqcEr7XsHit+28qifOTSx28H/Kh6ie+LyRfpLLYpIJIi1d\n"
            + "PBmMk/WL1lkkw6p/uADiuuiu6y3LgINHf/pL94pmaXNpceIL7JTC6FhLairLHHUX\n"
            + "KQFwDVfRAoGBAP8UUm7vjG+vJmHb+VL1buK1yorw4BUYDmPyDG5TV/x53crJLBLB\n"
            + "NdZRHIGJgO7EZzaq/Y2I/zSOApOD+t3dGLSkWqX9Q1ZYhyC8UpS59f5FXbrTiKKy\n"
            + "CLAUOfJangBgXAsaQgBW+nFNAQD1024tiQI/+xFBtVvaBgkPX9f/3IH/AoGBAO5O\n"
            + "5xNrUKn2iOFzA/OezNUyJLLZHy++PrUl3dX6TSi8SLLucTxbe0jY5WTT6CHMEjV9\n"
            + "y2BoEp5/75VWKLulHe2tsXD0vt0bOeUYuY+gxpy8yQHrvFdDVgJpty7Ojlq6HpNR\n"
            + "eaqe09oLAoj7101oe31TxXchgtVW3L9kbkSNWzATAoGBAKTx9DhOaUbTQQOo5nnx\n"
            + "wcmSeq4MAytAyhVxMP6qoEvgVj7Khdh+3hx/iOpvC1Pa509flORQQY8vgZT4lGGh\n"
            + "lldBrGiH9GJCjubpQJFyCxNosaqdHU7vx1RtT+dbF67woLSBP0rO89YOUGbZj+ZA\n"
            + "RceRrf3Dus3xl1OGgJjBmErrAoGBALW9PsS0huPXvDQTPuRAKv48K3ip1PrYH4KO\n"
            + "yksxhD6YuOicp4bcTX0UzHzEjreXphtdQAjZ1blC4DgHaTERj21/lV8Lh7Of29s3\n"
            + "q+w5NL67yp/IY+440BMvBCCSLkZKwp6e+CaC0hXrZ5eIWH4UPHkrteBQkjKY9+iO\n"
            + "cvmcw/o5AoGASquClO7yArARtxUQQgdUuOevE3SI1+IBlMGV7bfH/5Iy1pb71J6Z\n"
            + "JQODPwDT27YupfmZ8D+CQqNYgqojGIdjM/lsvSH44AJfKg/9B+1jx5gRps098DkW\n"
            + "hGnIPhavxX7VWSS80mLI8wDpEznPh8kzL54o+etSO6jyjbR8/bYf8B4=\n"
            + "-----END RSA PRIVATE KEY-----";

    @Test
    public void testKeyEncryptionWithGoodKeyPair() {
        KeySignerUtil keySignerUtil = new KeySignerUtil();
        String clearText = "this is a test!";
        String encryptedBase64 = keySignerUtil.encryptAndBase64(clearText, TESTING_PUBLIC_KEY);
        byte[] decrypted = keySignerUtil.decryptFromBase64(encryptedBase64, TESTING_PRIVATE_KEY);
        Assert.assertEquals(clearText, new String(decrypted));
    }

    @Test
    public void testEncryptionWithBadKey() {
        try {
            new KeySignerUtil().encryptAndBase64("foo", "bar");
            Assert.fail("call with bogus params should have failed");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("failed"));
        }

    }
}
