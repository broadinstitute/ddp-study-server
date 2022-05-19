package org.broadinstitute.dsm.model.filter.prefilter;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.prefilter.osteo.NewOsteoPreFilter;
import org.junit.Test;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.Assert.assertTrue;

public class StudyPreFilterTest {

    @Test
    public void fromPayload() {

        String newOsteoInstanceName = "osteo2";

        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder()
               .withInstanceName(newOsteoInstanceName)
               .build();

       ElasticSearchParticipantDto esDto = new ElasticSearchParticipantDto.Builder().build();

       Optional<StudyPreFilter> maybeNewOsteoPreFilter = StudyPreFilter.fromPayload(StudyPreFilterPayload.of(esDto, ddpInstanceDto));
       Optional<StudyPreFilter> maybeEmpty = StudyPreFilter.fromPayload(StudyPreFilterPayload.of(esDto, new DDPInstanceDto.Builder().build()));

       assertTrue(maybeNewOsteoPreFilter.get() instanceof NewOsteoPreFilter);

       try {
           maybeEmpty.get();
       } catch (NoSuchElementException nse) {
           assertTrue("maybeEmpty.get() represents no value as expected", true);
       }

    }
}