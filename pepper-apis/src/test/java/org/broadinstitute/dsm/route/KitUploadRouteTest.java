package org.broadinstitute.dsm.route;

import com.typesafe.config.Config;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.exception.UploadLineException;
import org.broadinstitute.dsm.model.ParticipantWrapper;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class KitUploadRouteTest {

    private static Config cfg;

    private static KitUploadRoute route;

    @BeforeClass
    public static void doFirst() throws Exception {
        TestHelper.setupDB();
        cfg = TestHelper.cfg;

        NotificationUtil notificationUtil = new NotificationUtil(cfg);
        route = new KitUploadRoute(notificationUtil);
    }

    @Test
    public void headerMissingShortId() {
        String fileContent = "firstName\tlastName\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "56\tSun\tMaid\t415 Main St\t\tCambridge\tMA\t2142\tUS";
        try {
            route.isFileValid(fileContent, null);
        }
        catch (FileColumnMissing e) {
            Assert.assertTrue(e.getMessage().endsWith("shortId"));
        }
    }


    @Test
    public void headerMissingFirstNameWithShortId() {
        String fileContent = "shortId\tlastName\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "56\tMaid\t415 Main St\t\tCambridge\tMA\t2142\tUS";
        try {
            route.isFileValid(fileContent, null);
        }
        catch (FileColumnMissing e) {
            Assert.assertTrue(e.getMessage().endsWith("firstName or signature"));
        }
    }

    @Test
    public void fileMissingFirstAndLastName() {
        String fileContent = "shortId\tfirstName\tlastName\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "56\t415 Main St\t\tCambridge\tMA\t2142\tUS";
        try {
            route.isFileValid(fileContent, "testrealm");
        }
        catch (UploadLineException e) {
            Assert.assertTrue(e.getMessage().endsWith("Error in line 2"));
        }
    }

    @Test
    public void fileUserNameDoesNotMatch() {
        String fileContent = "shortId\tfirstName\tlastName\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "56\tNebula\tGalaxy\t415 Main St\t\tCambridge\tMA\t2142\tUS";

        String[] rows = fileContent.split(System.lineSeparator());
        List<String> fieldNamesFromFileHeader = Arrays.asList(rows[0].trim().split(SystemUtil.SEPARATOR));

        Map<String, String> participantDataAsMap = route.getParticipantDataAsMap(rows[1], fieldNamesFromFileHeader, 1);

        String participantFirstNameFromDoc = participantDataAsMap.get("firstName");
        String participantLastNameFromDoc = participantDataAsMap.get("lastName");

        ParticipantWrapper testParticipant = participantFactory("Mickey", "Mouse", "");

        Assert.assertFalse(route.isKitUploadNameMatchesToEsName(participantFirstNameFromDoc, participantLastNameFromDoc, Optional.of(testParticipant)));
    }

    @Test
    public void fileUserDoesNotBelongToStudy() {
        String fileContent = "shortId\tfirstName\tlastName\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "56\tNebula\tGalaxy\t415 Main St\t\tCambridge\tMA\t2142\tUS";

        String[] rows = fileContent.split(System.lineSeparator());
        List<String> fieldNamesFromFileHeader = Arrays.asList(rows[0].trim().split(SystemUtil.SEPARATOR));

        Map<String, String> participantDataAsMap = route.getParticipantDataAsMap(rows[1], fieldNamesFromFileHeader, 1);

        String participantFirstNameFromDoc = participantDataAsMap.get("firstName");
        String participantLastNameFromDoc = participantDataAsMap.get("lastName");

        Assert.assertFalse(route.isKitUploadNameMatchesToEsName(participantFirstNameFromDoc, participantLastNameFromDoc, Optional.empty()));
    }

    private ParticipantWrapper participantFactory(String firstName, String lastName, String shortId) {
        ParticipantWrapper participant = new ParticipantWrapper();
        Map<String, Object> participantData = new HashMap<>();
        Map<String, String> participantProfile = new HashMap<>();
        participantProfile.put("firstName", firstName);
        participantProfile.put("lastName", lastName);
        if (ParticipantUtil.isHruid(shortId)) {
            participantProfile.put("hruid", shortId);
        } else {
            participantProfile.put("legacyShortId", shortId);
        }
        participantData.put("profile", participantProfile);
        participant.setData(participantData);
        return participant;
    }
}
