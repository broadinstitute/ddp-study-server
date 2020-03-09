package org.broadinstitute.ddp.service;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.google.maps.errors.InvalidRequestException;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.Geometry;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlusCode;
import com.google.openlocationcode.OpenLocationCode;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.client.ApiResult;
import org.broadinstitute.ddp.client.GoogleMapsClient;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.testcontainers.shaded.org.apache.commons.lang.StringUtils;

public class OLCServiceTest extends TxnAwareBaseTest {

    private static final String ADDRESS = "415 Main St, Cambridge, MA 02142, USA";
    private static final String PLUSCODE_FULL = "87JC9W76+5G";
    private static final String PLUSCODE_MORE = "87JC9W76+";
    private static final String PLUSCODE_MED = "87JC9W00+";
    private static final String PLUSCODE_LESS = "87JC0000+";
    private static final String PLUSCODE_LEAST = "87000000+";
    private static final double LAT = 42.362937;
    private static final double LNG = -71.088687;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Handle mockHandle;
    private JdbiUmbrellaStudy mockJdbiStudy;
    private JdbiUserStudyEnrollment mockEnrollment;
    private GoogleMapsClient mockMaps;
    private OLCService service;

    @Before
    public void init() {
        mockHandle = mock(Handle.class);
        mockJdbiStudy = mock(JdbiUmbrellaStudy.class);
        mockEnrollment = mock(JdbiUserStudyEnrollment.class);
        when(mockHandle.attach(JdbiUmbrellaStudy.class)).thenReturn(mockJdbiStudy);
        when(mockHandle.attach(JdbiUserStudyEnrollment.class)).thenReturn(mockEnrollment);

        mockMaps = mock(GoogleMapsClient.class);
        service = new OLCService(mockMaps);
    }

    @Test
    public void testGetAllOLCsForEnrolledParticipantsInStudy() {
        var enabledStudy = new StudyDto(1L, "study", "name", "irb", "url", 1L, 1L,
                OLCPrecision.MEDIUM, true, "email", true);

        when(mockJdbiStudy.findByStudyGuid(any())).thenReturn(enabledStudy);
        when(mockEnrollment.findAllOLCsForEnrolledParticipantsInStudy(any())).thenReturn(List.of(PLUSCODE_FULL));

        var actual = OLCService.getAllOLCsForEnrolledParticipantsInStudy(mockHandle, "study");
        assertNotNull(actual);
        assertNotNull(actual.getParticipantInfoList());
        assertEquals(1, actual.getParticipantInfoList().size());
        assertEquals(PLUSCODE_MED, actual.getParticipantInfoList().get(0).getLocation());
    }

    @Test
    public void testGetAllOLCsForEnrolledParticipantsInStudy_noParticipantsWithDefaultAddress() {
        var enabledStudy = new StudyDto(1L, "study", "name", "irb", "url", 1L, 1L,
                OLCPrecision.MEDIUM, true, "email", true);

        when(mockJdbiStudy.findByStudyGuid(any())).thenReturn(enabledStudy);
        when(mockEnrollment.findAllOLCsForEnrolledParticipantsInStudy(any())).thenReturn(Collections.emptyList());

        var actual = OLCService.getAllOLCsForEnrolledParticipantsInStudy(mockHandle, "study");
        assertTrue(actual.getParticipantInfoList().isEmpty());
    }

    @Test
    public void testGetAllOLCsForEnrolledParticipantsInStudy_publicDataSharingDisabled() {
        var disabledStudy = new StudyDto(1L, "study", "name", "irb", "url", 1L, 1L,
                OLCPrecision.MEDIUM, false, "email", true);

        when(mockJdbiStudy.findByStudyGuid(any())).thenReturn(disabledStudy);

        var actual = OLCService.getAllOLCsForEnrolledParticipantsInStudy(mockHandle, "study");
        assertNull(actual);
    }

    @Test
    public void testGetAllOLCsForEnrolledParticipantsInStudy_noOlcPrecisionSet() {
        var noPrecisionStudy = new StudyDto(1L, "study", "name", "irb", "url", 1L, 1L, null, false, "email", true);

        when(mockJdbiStudy.findByStudyGuid(any())).thenReturn(noPrecisionStudy);

        var actual = OLCService.getAllOLCsForEnrolledParticipantsInStudy(mockHandle, "study");
        assertNull(actual);
    }

    @Test
    public void testConvertPlusCodeToPrecision() {
        assertEquals("", OLCService.convertPlusCodeToPrecision("", OLCPrecision.MOST));
        assertEquals(PLUSCODE_FULL, OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.MOST));
        assertEquals(PLUSCODE_MORE, OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.MORE));
        assertEquals(PLUSCODE_MED, OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.MEDIUM));
        assertEquals(PLUSCODE_LESS, OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.LESS));
        assertEquals(PLUSCODE_LEAST, OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.LEAST));
    }

    @Test
    public void testConvertPlusCodeToPrecision_invalidCode() {
        thrown.expect(DDPException.class);
        thrown.expectMessage(containsString("is not valid"));
        OLCService.convertPlusCodeToPrecision("abc11+xyz22", OLCPrecision.LEAST);
    }

    @Test
    public void testConvertPlusCodeToPrecision_cannotConvertToMostPreciseWhenShortened() {
        thrown.expect(DDPException.class);
        thrown.expectMessage(containsString("is not precise enough"));
        OLCService.convertPlusCodeToPrecision(PLUSCODE_LESS, OLCPrecision.MEDIUM);
    }

    @Test
    public void testConvertPlusCodeToPrecision_nonStandardLength() {
        String shortenedPluscode = StringUtils.substring(PLUSCODE_FULL, 0, 5);
        thrown.expect(DDPException.class);
        thrown.expectMessage(shortenedPluscode + " is not valid");
        OLCService.convertPlusCodeToPrecision(shortenedPluscode, OLCPrecision.MEDIUM);
    }

    @Test
    public void testConvertPlusCodeToPrecision_blockSize() {
        double epsilon = 0;

        // longitude tests
        assertEquals(OLCPrecision.LEAST.getBlockSizeInDegrees(), new OpenLocationCode(
                OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.LEAST)).decode().getLongitudeWidth(),
                epsilon);
        assertEquals(OLCPrecision.LESS.getBlockSizeInDegrees(), new OpenLocationCode(
                OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.LESS)).decode().getLongitudeWidth(),
                epsilon);
        assertEquals(OLCPrecision.MEDIUM.getBlockSizeInDegrees(), new OpenLocationCode(
                OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.MEDIUM)).decode().getLongitudeWidth(),
                epsilon);
        assertEquals(OLCPrecision.MORE.getBlockSizeInDegrees(), new OpenLocationCode(
                OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.MORE)).decode().getLongitudeWidth(),
                epsilon);
        assertEquals(OLCPrecision.MOST.getBlockSizeInDegrees(), new OpenLocationCode(
                OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.MOST)).decode().getLongitudeWidth(),
                epsilon);

        // latitude tests
        assertEquals(OLCPrecision.LEAST.getBlockSizeInDegrees(), new OpenLocationCode(
                OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.LEAST)).decode().getLatitudeHeight(),
                epsilon);
        assertEquals(OLCPrecision.LESS.getBlockSizeInDegrees(), new OpenLocationCode(
                OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.LESS)).decode().getLatitudeHeight(),
                epsilon);
        assertEquals(OLCPrecision.MEDIUM.getBlockSizeInDegrees(), new OpenLocationCode(
                OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.MEDIUM)).decode().getLatitudeHeight(),
                epsilon);
        assertEquals(OLCPrecision.MORE.getBlockSizeInDegrees(), new OpenLocationCode(
                OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.MORE)).decode().getLatitudeHeight(),
                epsilon);
        assertEquals(OLCPrecision.MOST.getBlockSizeInDegrees(), new OpenLocationCode(
                OLCService.convertPlusCodeToPrecision(PLUSCODE_FULL, OLCPrecision.MOST)).decode().getLatitudeHeight(),
                epsilon);
    }

    @Test
    public void testCalculatePlusCodeWithPrecision() {
        var res1 = new GeocodingResult();
        res1.plusCode = new PlusCode();
        res1.plusCode.globalCode = PLUSCODE_FULL;
        when(mockMaps.lookupGeocode(any())).thenReturn(ApiResult.ok(200, new GeocodingResult[] {res1}));
        assertEquals(PLUSCODE_MED, service.calculatePlusCodeWithPrecision(ADDRESS, OLCPrecision.MEDIUM));
    }

    @Test
    public void testCalculatePlusCodeWithPrecision_fromLatLongLocation() {
        var res1 = new GeocodingResult();
        res1.geometry = new Geometry();
        res1.geometry.location = new LatLng(LAT, LNG);
        when(mockMaps.lookupGeocode(any())).thenReturn(ApiResult.ok(200, new GeocodingResult[] {res1}));
        assertEquals(PLUSCODE_MED, service.calculatePlusCodeWithPrecision(ADDRESS, OLCPrecision.MEDIUM));
    }

    @Test
    public void testCalculatePlusCodeWithPrecision_moreThanOneResult() {
        var res1 = new GeocodingResult();
        res1.plusCode = new PlusCode();
        res1.plusCode.globalCode = PLUSCODE_FULL;
        var res2 = new GeocodingResult();
        res2.plusCode = new PlusCode();
        res2.plusCode.globalCode = PLUSCODE_MORE;

        when(mockMaps.lookupGeocode(any())).thenReturn(
                ApiResult.ok(200, new GeocodingResult[] {res1, res2}));

        assertNull(service.calculatePlusCodeWithPrecision(ADDRESS, OLCPrecision.MEDIUM));
    }

    @Test
    public void testCalculatePlusCodeWithPrecision_error() {
        when(mockMaps.lookupGeocode(any())).thenReturn(
                ApiResult.err(400, new InvalidRequestException("from test")));
        assertNull(service.calculatePlusCodeWithPrecision(ADDRESS, OLCPrecision.MEDIUM));
    }

    @Test
    public void testCalculatePlusCodeWithPrecision_exception() {
        when(mockMaps.lookupGeocode(any())).thenReturn(
                ApiResult.thrown(new IOException("from test")));
        assertNull(service.calculatePlusCodeWithPrecision(ADDRESS, OLCPrecision.MEDIUM));
    }

    @Test
    public void testCalculateFullPlusCode() {
        var res1 = new GeocodingResult();
        res1.plusCode = new PlusCode();
        res1.plusCode.globalCode = PLUSCODE_FULL;
        when(mockMaps.lookupGeocode(any())).thenReturn(ApiResult.ok(200, new GeocodingResult[] {res1}));

        var address = new MailAddress(
                "name", "415 Main St", null, "Cambridge", "MA", "US", "02142",
                null, null, null, null, false);

        assertEquals(PLUSCODE_FULL, service.calculateFullPlusCode(address));
    }
}
