
package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import static org.broadinstitute.dsm.model.filter.postfilter.osteo.OldOsteoPostFilterStrategyTest.randomString;

import java.util.List;

import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.junit.Assert;
import org.junit.Test;

public class OldOsteoPostFilterTest {

    @Test
    public void twoCorrectActivities() {
        var esPtDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(List.of(
                        new Activities("FAKE_ACTIVITY", "v1"),
                        new Activities("LOVEDONE", "v1"),
                        new Activities("FAKE_ACTIVITY_2", "v2"),
                        new Activities("PREQUAL", "v2"),
                        new Activities("RELEASE_MINOR", "v1"),
                        new Activities("FAKE_ACTIVITY_3", "v1")
                )).build();
        new OldOsteoPostFilter(esPtDto, null).filter();
        Assert.assertEquals(2, esPtDto.getActivities().size());
    }

    @Test
    public void allCorrectActivities() {
        var esPtDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(List.of(
                        new Activities("PREQUAL", "v1"),
                        new Activities("RELEASE_SELF", "v1"),
                        new Activities("PARENTAL_CONSENT", "v1")
                )).build();
        new OldOsteoPostFilter(esPtDto, null).filter();
        Assert.assertEquals(3, esPtDto.getActivities().size());
    }
    
    @Test
    public void noneCorrectActivities() {
        var esPtDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(List.of(
                        new Activities(randomString(4), "v3"),
                        new Activities(randomString(10), "v2"),
                        new Activities(randomString(3), "v2"),
                        new Activities(randomString(14), "v2")
                )).build();
        new OldOsteoPostFilter(esPtDto, null).filter();
        Assert.assertEquals(0, esPtDto.getActivities().size());
    }

}
