
package org.broadinstitute.dsm.model.filter.prefilter.osteo;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilterPayload;
import org.junit.Test;

public class OldOsteoPreFilterTest {

    @Test
    public void filter() {
        int ddpInstanceId = 1;
        String newOsteoInstanceName = "Osteo";
        List<Activities> activities = new ArrayList<>(List.of(
                new Activities("CONSENT", "v2"),
                new Activities("PREQUAL", "v2"),
                new Activities("FAMILY_HISTORY", "v1"),
                new Activities("ABOUT_YOU", "v1")
        ));
        ElasticSearchParticipantDto esDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(activities)
                .build();
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder()
                .withInstanceName(newOsteoInstanceName)
                .withDdpInstanceId(ddpInstanceId)
                .build();
        Optional<StudyPreFilter> preFilter = StudyPreFilter.fromPayload(StudyPreFilterPayload.of(esDto, ddpInstanceDto));
        preFilter.ifPresent(StudyPreFilter::filter);
        assertEquals(2, esDto.getActivities().size());
        assertEquals(List.of("FAMILY_HISTORY", "ABOUT_YOU"), esDto.getActivities().stream()
                .map(Activities::getActivityCode)
                .collect(Collectors.toList()));
    }
}
