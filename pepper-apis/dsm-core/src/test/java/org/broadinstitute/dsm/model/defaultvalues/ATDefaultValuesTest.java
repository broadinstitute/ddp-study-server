
package org.broadinstitute.dsm.model.defaultvalues;

import java.util.List;

import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.junit.Assert;
import org.junit.Test;

public class ATDefaultValuesTest {

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
}
