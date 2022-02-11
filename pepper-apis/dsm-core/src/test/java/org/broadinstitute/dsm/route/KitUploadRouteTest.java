package org.broadinstitute.dsm.route;

import com.typesafe.config.Config;
import org.broadinstitute.dsm.TestHelper;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.exception.UploadLineException;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

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

        ESProfile esProfile = new ESProfile();
        esProfile.setFirstName("Mickey");
        esProfile.setLastName("Mouse");
        esProfile.setHruid("");
        ElasticSearchParticipantDto elasticSearchParticipantDto = new ElasticSearchParticipantDto.Builder()
                .withProfile(esProfile)
                .build();
        Assert.assertNotEquals("", route.checkKitUploadNameMatchesToEsName(participantFirstNameFromDoc, participantLastNameFromDoc, elasticSearchParticipantDto));
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

        ElasticSearchParticipantDto elasticSearchParticipantDto = new ElasticSearchParticipantDto.Builder().build();
        Assert.assertNotEquals("", route.checkKitUploadNameMatchesToEsName(participantFirstNameFromDoc, participantLastNameFromDoc, elasticSearchParticipantDto));
    }

    private ParticipantWrapperDto participantFactory(String firstName, String lastName, String shortId) {
        ParticipantWrapperDto participant = new ParticipantWrapperDto();
        ESProfile esProfile = new ESProfile();
        esProfile.setFirstName(firstName);
        esProfile.setLastName(lastName);
        if (ParticipantUtil.isHruid(shortId)) {
            esProfile.setHruid(shortId);
        } else {
            esProfile.setLegacyAltPid(shortId);
        }

        ElasticSearchParticipantDto elasticSearchParticipantDto = new ElasticSearchParticipantDto.Builder()
                .withProfile(esProfile)
                .build();
        participant.setEsData(elasticSearchParticipantDto);
        return participant;
    }
}
