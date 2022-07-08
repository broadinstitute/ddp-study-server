
package org.broadinstitute.dsm.model.filter.prefilter;

import static org.junit.Assert.assertTrue;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.prefilter.osteo.NewOsteoPreFilter;
import org.broadinstitute.dsm.model.filter.prefilter.osteo.OldOsteoPreFilter;
import org.junit.Test;

public class StudyPreFilterTest {

    @Test
    public void buildNewOsteoInstance() {
        String newOsteoInstanceName = "osteo2";
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().withInstanceName(newOsteoInstanceName).build();
        ElasticSearchParticipantDto esDto = new ElasticSearchParticipantDto.Builder().build();
        Optional<StudyPreFilter> maybeNewOsteoPreFilter = StudyPreFilter.fromPayload(StudyPreFilterPayload.of(esDto, ddpInstanceDto));
        assertTrue(maybeNewOsteoPreFilter.get() instanceof NewOsteoPreFilter);

    }

    @Test
    public void buildOldOsteoInstance() {
        String oldOsteoInstanceName = "Osteo";
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().withInstanceName(oldOsteoInstanceName).build();
        ElasticSearchParticipantDto esDto = new ElasticSearchParticipantDto.Builder().build();
        Optional<StudyPreFilter> maybeOldOsteoPreFilter = StudyPreFilter.fromPayload(StudyPreFilterPayload.of(esDto, ddpInstanceDto));
        assertTrue(maybeOldOsteoPreFilter.get() instanceof OldOsteoPreFilter);

    }

    @Test
    public void buildEmpty() {
        Optional<StudyPreFilter> maybeEmpty = StudyPreFilter.fromPayload(StudyPreFilterPayload.of(null,
                new DDPInstanceDto.Builder().build()));
        try {
            maybeEmpty.get();
        } catch (NoSuchElementException nse) {
            assertTrue("maybeEmpty.get() represents no value, thereby throws NoSuchElementException as expected", true);
        }

    }
}
