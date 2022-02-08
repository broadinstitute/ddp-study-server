package org.broadinstitute.ddp.event.pubsubtask.api;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskResult.PubSubTaskResultType.SUCCESS;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.TASK_TYPE__UPDATE_PROFILE;
import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

public class PubSubTaskResultTest {

    @Test
    public void testGenerateDefaultPayload() {
        var errorText = "User profile is not found for guid ABC";
        var pubSubTaskResult = new PubSubTaskResult(ERROR, errorText, generatePubSubTask());
        assertEquals("{\"errorMessage\":\"User profile is not found for guid ABC\",\"resultType\":\"ERROR\"}",
                pubSubTaskResult.getPayloadJson());

        pubSubTaskResult = new PubSubTaskResult(SUCCESS, null, generatePubSubTask());
        assertEquals("{\"resultType\":\"SUCCESS\"}", pubSubTaskResult.getPayloadJson());
    }

    @Test
    public void testGenerateDefaultAttributes() {
        var pubSubTaskResult = new PubSubTaskResult(SUCCESS, null, generatePubSubTask());

        assertEquals(3, pubSubTaskResult.getAttributes().size());
        assertEquals("{user_id=user_aaa, taskType=UPDATE_PROFILE, taskMessageId=1}", pubSubTaskResult.getAttributes().toString());
    }

    private PubSubTask generatePubSubTask() {
        return new PubSubTask("1", TASK_TYPE__UPDATE_PROFILE,
                Map.of("user_id", "user_aaa"), "{'extraName': 'extaName_aaa'}");
    }
}
