
package org.broadinstitute.dsm.model.filter.prefilter;

import static org.junit.Assert.assertTrue;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilterPayload;
import org.broadinstitute.dsm.model.filter.postfilter.osteo.DsmDdpInstanceIdPostFilter;
import org.broadinstitute.dsm.model.filter.postfilter.osteo.ActivityAndDdpIdPostFilter;
import org.junit.Test;

public class StudyPostFilterTest {

    @Test
    public void buildNewOsteoInstance() {
        String newOsteoInstanceName = "osteo2";
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().withInstanceName(newOsteoInstanceName).build();
        ElasticSearchParticipantDto esDto = new ElasticSearchParticipantDto.Builder().build();
        Optional<StudyPostFilter> maybeNewOsteoPreFilter = StudyPostFilter.fromPayload(StudyPostFilterPayload.of(esDto, ddpInstanceDto));
        assertTrue(maybeNewOsteoPreFilter.get() instanceof DsmDdpInstanceIdPostFilter);

    }

    @Test
    public void buildOldOsteoInstance() {
        String oldOsteoInstanceName = "Osteo";
        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder().withInstanceName(oldOsteoInstanceName).build();
        ElasticSearchParticipantDto esDto = new ElasticSearchParticipantDto.Builder().build();
        Optional<StudyPostFilter> maybeOldOsteoPreFilter = StudyPostFilter.fromPayload(StudyPostFilterPayload.of(esDto, ddpInstanceDto));
        assertTrue(maybeOldOsteoPreFilter.get() instanceof ActivityAndDdpIdPostFilter);

    }

    @Test
    public void buildEmpty() {
        Optional<StudyPostFilter> maybeEmpty = StudyPostFilter.fromPayload(StudyPostFilterPayload.of(null,
                new DDPInstanceDto.Builder().build()));
        try {
            maybeEmpty.get();
        } catch (NoSuchElementException nse) {
            assertTrue("maybeEmpty.get() represents no value, thereby throws NoSuchElementException as expected", true);
        }

    }
}
