
package org.broadinstitute.dsm.model.patch.process;

import org.broadinstitute.dsm.model.patch.Patch;
import org.junit.Assert;
import org.junit.Test;

public class BasePatchPreProcessorTest {

    @Test
    public void produceDefaultPatchPreProcessor() {
        var defaultPreProcessor = BasePatchPreProcessor.produce(PatchPreProcessorPayload.of("r", "ddpParticipantId"));
        var patch = defaultPreProcessor.process(new Patch("ddpParticipantId", "artificial_guid"));
        Assert.assertEquals(patch, new Patch("ddpParticipantId", "artificial_guid"));
    }

    @Test
    public void produceParentRelatedPatchPreProcessor() {
        var preProcessor = BasePatchPreProcessor.produce(PatchPreProcessorPayload.of("anythingOtherThan 'r'", "ddpParticipantId"));
        Assert.assertTrue(preProcessor instanceof ParentRelatedPatchPreProcessor);
    }

}