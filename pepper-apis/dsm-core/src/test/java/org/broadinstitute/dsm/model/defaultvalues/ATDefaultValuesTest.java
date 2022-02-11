package org.broadinstitute.dsm.model.defaultvalues;

import org.broadinstitute.dsm.model.ddp.DDPActivityConstants;
import org.broadinstitute.dsm.model.elastic.ESActivities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class ATDefaultValuesTest {

    @Test
    public void isSelfOrDependentParticipant() {
        ATDefaultValues defaultValues = new ATDefaultValues();

        ESActivities esActivities = new ESActivities();
        esActivities.setActivityCode(ATDefaultValues.ACTIVITY_CODE_PREQUAL);
        esActivities.setQuestionsAnswers(Collections.singletonList(Map.of(
                DDPActivityConstants.DDP_ACTIVITY_STABLE_ID, ATDefaultValues.PREQUAL_SELF_DESCRIBE,
                ATDefaultValues.QUESTION_ANSWER, List.of(ATDefaultValues.SELF_DESCRIBE_CHILD_DIAGNOSED))));

        ESActivities esActivities2 = new ESActivities();
        esActivities2.setActivityCode(ATDefaultValues.ACTIVITY_CODE_PREQUAL);
        esActivities2.setQuestionsAnswers(Collections.singletonList(Map.of(
                DDPActivityConstants.DDP_ACTIVITY_STABLE_ID, ATDefaultValues.PREQUAL_SELF_DESCRIBE,
                ATDefaultValues.QUESTION_ANSWER, List.of(ATDefaultValues.SELF_DESCRIBE_CHILD_DIAGNOSED))));

        ElasticSearchParticipantDto participantDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(List.of(esActivities))
                .build();

        defaultValues.elasticSearchParticipantDto = participantDto;

        Assert.assertTrue(defaultValues.isSelfOrDependentParticipant());

        participantDto.setActivities(List.of(esActivities2));

        Assert.assertTrue(defaultValues.isSelfOrDependentParticipant());
    }
}