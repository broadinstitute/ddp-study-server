
package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import java.util.Optional;
import java.util.stream.IntStream;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterStrategy;
import org.junit.Assert;
import org.junit.Test;

public class DdpInstanceIdPostFilterStrategyTest {

    private static final int NEW_OSTEO_ARTIFICIAL_INSTANCE_ID = 100;

    private final DDPInstanceDto ddpInstanceDto =
            new DDPInstanceDto.Builder()
            .withDdpInstanceId(NEW_OSTEO_ARTIFICIAL_INSTANCE_ID).build();

    private final StudyPostFilterStrategy<HasDdpInstanceId> newOsteoFilter = new DdpInstanceIdPostFilterStrategy(ddpInstanceDto);

    @Test
    public void correctInstanceId() {
        Assert.assertTrue(newOsteoFilter.test(() -> Optional.of(100L)));
    }

    @Test
    public void incorrectInstanceId() {
        IntStream.iterate(1, i -> i + 1)
                .limit(50)
                .boxed()
                .mapToLong(Integer::longValue)
                .forEach(ddpInstanceId -> Assert.assertFalse(newOsteoFilter.test(() -> Optional.of(ddpInstanceId))));
    }

}
