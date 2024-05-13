package org.broadinstitute.dsm.service.adminoperation;

import java.util.Arrays;
import java.util.Collection;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UpdateKitToLegacyIdsRequestTest {

    private String oldCollaboratorSampleId;
    private String newCollaboratorSampleId;
    private String newCollaboratorParticipantId;
    private String shortId;
    private String legacyShortId;
    private String expectedExceptionMessage;
    DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().withInstanceName("REPATIENT_KIT_INSTANCE").build();

    public UpdateKitToLegacyIdsRequestTest(String oldCollaboratorSampleId, String newCollaboratorSampleId,
                                           String newCollaboratorParticipantId, String shortId, String legacyShortId,
                                           String expectedExceptionMessage) {
        this.oldCollaboratorSampleId = oldCollaboratorSampleId;
        this.newCollaboratorSampleId = newCollaboratorSampleId;
        this.newCollaboratorParticipantId = newCollaboratorParticipantId;
        this.shortId = shortId;
        this.legacyShortId = legacyShortId;
        this.expectedExceptionMessage = expectedExceptionMessage;
    }

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { null, "newSampleId", "newParticipantId", "shortId", "legacyId", "Missing required field: currentCollaboratorSampleId" },
                { "oldSampleId", null, "newParticipantId", "shortId", "legacyId", "Missing required field: newCollaboratorSampleId" },
                { "oldSampleId", "newSampleId", "newParticipantId", null, "legacyId", "Missing required field: shortId" },
                { "oldSampleId", "newSampleId", null, "shortId", "legacyId", "Missing required field: newCollaboratorParticipantId" }
        });
    }

    @Test
    public void testVerify_MissingFields() {
        try {
            UpdateKitToLegacyIdsRequest request = new UpdateKitToLegacyIdsRequest(
                    oldCollaboratorSampleId, newCollaboratorSampleId, newCollaboratorParticipantId, shortId, legacyShortId);
            request.verify(ddpInstanceDto);  // Assuming ddpInstanceDto is available in your test context
            Assert.fail("Expected an exception to be thrown");
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertEquals(expectedExceptionMessage, e.getMessage());
        }
    }
}
