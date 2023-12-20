package org.broadinstitute.dsm.model.defaultvalues;

import java.util.List;

import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ATDefaultValuesTest extends DbAndElasticBaseTest {

    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final String instanceName = "atdefaultvaluestest";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static ParticipantDto testParticipant = null;

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
        "elastic/atcpSettings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
    }

    @AfterClass
    public static void tearDown() {
        try {
            Assert.assertNull(testParticipant);
            ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e);
        }
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @Test
    public void isSelfOrDependentParticipant() {
        ATDefaultValues defaultValues = new ATDefaultValues();

        Activities esActivities = new Activities();
        esActivities.setActivityCode(ATDefaultValues.ACTIVITY_CODE_REGISTRATION);
        esActivities.setStatus(ATDefaultValues.COMPLETE);

        Activities esActivities2 = new Activities();
        esActivities2.setActivityCode(ATDefaultValues.ACTIVITY_CODE_REGISTRATION);
        esActivities2.setStatus(ATDefaultValues.COMPLETE);

        ElasticSearchParticipantDto participantDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(List.of(esActivities))
                .build();

        defaultValues.elasticSearchParticipantDto = participantDto;

        Assert.assertTrue(defaultValues.isParticipantRegistrationComplete());

        participantDto.setActivities(List.of(esActivities2));

        Assert.assertTrue(defaultValues.isParticipantRegistrationComplete());
    }

    @Test
    public void testSetDefaultValuesExceptionMessageWhenParticipantIdNotFound() {
        String nonexistentParticipantId = "NOT_REAL_ID";
        ATDefaultValues atDefaultValues = new ATDefaultValues();
        try {
            atDefaultValues.generateDefaults(instanceName, nonexistentParticipantId);
        } catch (ESMissingParticipantDataException e) {
            Assert.assertTrue(String.format("Error message should include the queried participant id %s.  The "
                    + "message given is %s", nonexistentParticipantId, e.getMessage()),
                    e.getMessage().toUpperCase().contains("PARTICIPANT " + nonexistentParticipantId));
        }
    }
}
