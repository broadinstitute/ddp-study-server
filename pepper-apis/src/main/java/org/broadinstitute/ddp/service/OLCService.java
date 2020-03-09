package org.broadinstitute.ddp.service;

import static com.google.openlocationcode.OpenLocationCode.PADDING_CHARACTER;
import static java.util.stream.Collectors.toList;

import java.util.List;

import com.google.maps.model.GeocodingResult;
import com.google.openlocationcode.OpenLocationCode;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.GoogleMapsClient;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.study.ParticipantInfo;
import org.broadinstitute.ddp.model.study.StudyParticipantsInfo;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OLCService {

    public static final OLCPrecision DEFAULT_OLC_PRECISION = OLCPrecision.MOST;

    private static final Logger LOG = LoggerFactory.getLogger(OLCService.class);
    private static final int PRECISION_DELIMITER = 8;
    private static final int FULL_PLUS_CODE_LENGTH = 11;

    private final GoogleMapsClient maps;

    /**
     * For a given pluscode, make it as precise/generic as specified to hide exact address. This is done by
     * incrementally removing specific values from end of pluscode and converting into 0's. For more information see:
     * https://plus.codes/howitworks, also see: https://en.wikipedia.org/wiki/Open_Location_Code#Specification.
     *
     * @param plusCode  the exact 11 character pluscode
     * @param precision the degree of precision of the pluscode requested
     * @return the (potentially) more vague pluscode address
     */
    public static String convertPlusCodeToPrecision(String plusCode, OLCPrecision precision) {
        if (OpenLocationCode.isValidCode(plusCode)) {
            if (canConvertOLCToTargetPrecision(plusCode, precision)) {
                String lessPreciseOlc = plusCode.substring(0, precision.getCodeLength())
                        + StringUtils.repeat("0", PRECISION_DELIMITER - precision.getCodeLength());
                if (!lessPreciseOlc.endsWith("+") && lessPreciseOlc.length() < FULL_PLUS_CODE_LENGTH) {
                    lessPreciseOlc = lessPreciseOlc.concat("+");
                }
                return lessPreciseOlc;
            } else {
                throw new DDPException("Plus code " + plusCode + " is not precise enough to convert"
                        + " to desired precision.");
            }
        } else if (StringUtils.isEmpty(plusCode)) {
            return plusCode;
        } else {
            throw new DDPException("Pluscode " + plusCode + " is not valid.");
        }
    }

    private static boolean canConvertOLCToTargetPrecision(String plusCode, OLCPrecision targetPrecision) {
        // we can always convert to a lower precision from the most precise
        if (plusCode.length() == FULL_PLUS_CODE_LENGTH) {
            return true;
        }

        int indexOfPaddingChar = plusCode.indexOf(PADDING_CHARACTER);
        int indexOfTargetPaddingChar = targetPrecision.getCodeLength();

        // can convert a MORE precise OLC to anything other than MOST precise
        if (indexOfPaddingChar == -1) {
            return targetPrecision != OLCPrecision.MOST;
        } else if (indexOfPaddingChar >= indexOfTargetPaddingChar) {
            // otherwise, all other precisions have padding so we need to make sure the padding is at
            // least as long as (if not longer than) the target valid char length we want
            return true;
        } else {
            return false;
        }
    }

    /**
     * For a study, get all OLCs (repeat identical ones) for enrolled participants.
     *
     * @param studyGuid the study guid
     * @return the enrolled participant information object
     */
    public static StudyParticipantsInfo getAllOLCsForEnrolledParticipantsInStudy(Handle handle, String studyGuid) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

        if (!studyDto.isPublicDataSharingEnabled()) {
            LOG.error("Study " + studyGuid + " does not allow location sharing.");
            return null;
        }

        OLCPrecision precision = studyDto.getOlcPrecision();
        if (precision == null) {
            LOG.error("Study " + studyGuid + " has location sharing enabled but no plus code precision set in database.");
            return null;
        }

        List<String> olcsForStudy = handle.attach(JdbiUserStudyEnrollment.class).findAllOLCsForEnrolledParticipantsInStudy(studyGuid);

        return new StudyParticipantsInfo(
                olcsForStudy
                        .stream()
                        .map(olc -> new ParticipantInfo(convertPlusCodeToPrecision(olc, precision)))
                        .collect(toList())
        );
    }

    public OLCService(String geocodingKey) {
        this(new GoogleMapsClient(geocodingKey));
    }

    public OLCService(GoogleMapsClient mapsClient) {
        this.maps = mapsClient;
    }

    /**
     * Make call to Google geocoding API with a written out address, get plus code
     *
     * @param fullAddress the mail address to calculate the plus code of
     * @param precision   the pluscode precision desired. see {@link OLCPrecision}
     * @return the corresponding plus code at the specified precision
     */
    public String calculatePlusCodeWithPrecision(String fullAddress, OLCPrecision precision) {
        var res = maps.lookupGeocode(fullAddress);
        if (res.getStatusCode() != 200) {
            var e = res.hasThrown() ? res.getThrown() : res.getError();
            LOG.warn("Location: " + fullAddress + " could not be found on google maps services", e);
            return null;
        }
        GeocodingResult[] results = res.getBody();

        String plusCode;
        if (results.length != 1) {
            LOG.warn("Address: " + fullAddress + " had " + results.length + " results so we could not support assigning a pluscode");
            return null;
        } else if (results[0].plusCode == null) {
            double lat = results[0].geometry.location.lat;
            double lng = results[0].geometry.location.lng;

            plusCode = OpenLocationCode.encode(lat, lng);
        } else {
            plusCode = results[0].plusCode.globalCode;
        }
        return convertPlusCodeToPrecision(plusCode, precision);
    }

    /**
     * Override function for calculatePlusCodeWithPrecision to be used when given a {@link MailAddress} object
     *
     * @param mailAddress the mail address to calculate the plus code of
     * @param precision   the pluscode precision desired. see {@link OLCPrecision}
     * @return the corresponding plus code at the specified precision
     */
    public String calculatePlusCodeWithPrecision(MailAddress mailAddress, OLCPrecision precision) {
        String fullAddress = mailAddress.toAddressString();

        return calculatePlusCodeWithPrecision(fullAddress, precision);
    }

    /**
     * Override function for calculatePlusCodeWithPrecision to be used when calculating the plus code for a new/ updated address.
     *
     * @param mailAddress the mail address to calculate the plus code of
     * @return the corresponding exact plus code to the highest precision
     */
    public String calculateFullPlusCode(MailAddress mailAddress) {
        return calculatePlusCodeWithPrecision(mailAddress, OLCPrecision.MOST);
    }
}
