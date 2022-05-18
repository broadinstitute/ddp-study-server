package org.broadinstitute.dsm.model.filter.prefilter;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.prefilter.osteo.NewOsteoPreFilter;
import org.junit.Test;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.Assert.assertTrue;

public class PreFilterTest {

    @Test
    public void fromPayload() {

        String newOsteoInstanceName = "osteo2";

        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder()
               .withInstanceName(newOsteoInstanceName)
               .build();

       ElasticSearchParticipantDto esDto = new ElasticSearchParticipantDto.Builder().build();

       Optional<PreFilter> maybeNewOsteoPreFilter = PreFilter.fromPayload(PreFilterPayload.of(esDto, ddpInstanceDto));
       Optional<PreFilter> maybeEmpty = PreFilter.fromPayload(PreFilterPayload.of(esDto, new DDPInstanceDto.Builder().build()));

       assertTrue(maybeNewOsteoPreFilter.get() instanceof NewOsteoPreFilter);

       try {
           maybeEmpty.get();
       } catch (NoSuchElementException nse) {
           assertTrue("maybeEmpty.get() represents no value as expected", true);
       }

    }
}