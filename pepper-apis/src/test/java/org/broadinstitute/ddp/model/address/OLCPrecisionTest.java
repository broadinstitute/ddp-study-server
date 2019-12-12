package org.broadinstitute.ddp.model.address;

import static org.junit.Assert.assertEquals;

import com.google.openlocationcode.OpenLocationCode;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.service.OLCService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.testcontainers.shaded.org.apache.commons.lang.StringUtils;

public class OLCPrecisionTest {

    private static final String testOLC = "849VCWF8+24";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testGetOpenLocationCodeLimitSizes() {
        double epsilon = 0;

        // longitude tests
        assertEquals(OLCPrecision.LEAST.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.LEAST)).decode().getLongitudeWidth(),
                epsilon);
        assertEquals(OLCPrecision.LESS.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.LESS)).decode().getLongitudeWidth(),
                epsilon);
        assertEquals(OLCPrecision.MEDIUM.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.MEDIUM)).decode().getLongitudeWidth(),
                epsilon);
        assertEquals(OLCPrecision.MORE.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.MORE)).decode().getLongitudeWidth(),
                epsilon);
        assertEquals(OLCPrecision.MOST.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.MOST)).decode().getLongitudeWidth(),
                epsilon);

        // latitude tests
        assertEquals(OLCPrecision.LEAST.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.LEAST)).decode().getLatitudeHeight(),
                epsilon);
        assertEquals(OLCPrecision.LESS.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.LESS)).decode().getLatitudeHeight(),
                epsilon);
        assertEquals(OLCPrecision.MEDIUM.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.MEDIUM)).decode().getLatitudeHeight(),
                epsilon);
        assertEquals(OLCPrecision.MORE.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.MORE)).decode().getLatitudeHeight(),
                epsilon);
        assertEquals(OLCPrecision.MOST.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.MOST)).decode().getLatitudeHeight(),
                epsilon);
    }

    @Test
    public void testConvertPlusCode_failsCorrectly_whenAskForMostPrecisionButWrongLength() {
        String shortenedPluscode = OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.LEAST);

        thrown.expect(DDPException.class);
        thrown.expectMessage("Plus code " + shortenedPluscode + " isn't precise enough to convert"
                        + " to desired precision.");
        OLCService.convertPlusCodeToPrecision(shortenedPluscode, OLCPrecision.MOST);
    }

    @Test
    public void testConvertPlusCode_failsCorrectly_whenNotAStandardLength() {
        String shortenedPluscode = StringUtils.substring(testOLC, 0, 5);

        thrown.expect(DDPException.class);
        thrown.expectMessage("Pluscode " + shortenedPluscode + " is not valid.");
        OLCService.convertPlusCodeToPrecision(shortenedPluscode, OLCPrecision.MEDIUM);
    }

    @Test
    public void testConvertPlusCode_happyPath_whenPluscodeIsNotMostPrecise() {
        String shortenedPluscode = OLCService.convertPlusCodeToPrecision(testOLC, OLCPrecision.MORE);

        double epsilon = 0;

        assertEquals(OLCPrecision.LESS.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(shortenedPluscode, OLCPrecision.LESS))
                        .decode().getLongitudeWidth(),
                epsilon);
        assertEquals(OLCPrecision.LESS.getBlockSizeInDegrees(),
                new OpenLocationCode(OLCService.convertPlusCodeToPrecision(shortenedPluscode, OLCPrecision.LESS))
                        .decode().getLatitudeHeight(),
                epsilon);
    }

}
