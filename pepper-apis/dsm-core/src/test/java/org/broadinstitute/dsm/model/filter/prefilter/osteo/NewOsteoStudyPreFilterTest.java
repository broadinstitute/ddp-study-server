
package org.broadinstitute.dsm.model.filter.prefilter.osteo;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilter;
import org.broadinstitute.dsm.model.filter.prefilter.StudyPreFilterPayload;
import org.junit.Test;


public class NewOsteoStudyPreFilterTest {

    @Test
    public void filter() {
        int ddpInstanceId = 1;
        String oldOsteoInstanceName = "osteo2";
        Dsm esDsm = new Dsm();

        esDsm.setMedicalRecord(new ArrayList<>(List.of(
                new MedicalRecord(1),
                new MedicalRecord(2),
                new MedicalRecord(1))));

        esDsm.setOncHistoryDetail(new ArrayList<>(List.of(
                new OncHistoryDetail(2),
                new OncHistoryDetail(2),
                new OncHistoryDetail(1))));

        esDsm.setKitRequestShipping(new ArrayList<>(List.of(
                new KitRequestShipping(1),
                new KitRequestShipping(1),
                new KitRequestShipping(1))));

        ElasticSearchParticipantDto esDto = new ElasticSearchParticipantDto.Builder()
                .withDsm(esDsm)
                .build();

        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder()
                .withInstanceName(oldOsteoInstanceName)
                .withDdpInstanceId(ddpInstanceId)
                .build();

        Optional<StudyPreFilter> preFilter = StudyPreFilter.fromPayload(StudyPreFilterPayload.of(esDto, ddpInstanceDto));
        preFilter.ifPresent(StudyPreFilter::filter);

        assertEquals(2, esDsm.getMedicalRecord().size());
        assertEquals(List.of(1L, 1L), esDsm.getMedicalRecord().stream().map(MedicalRecord::getDdpInstanceId).collect(Collectors.toList()));

        assertEquals(1, esDsm.getOncHistoryDetail().size());
        assertEquals(List.of(1L), esDsm.getOncHistoryDetail().stream().map(OncHistoryDetail::getDdpInstanceId)
                .collect(Collectors.toList()));

        assertEquals(3, esDsm.getKitRequestShipping().size());
        assertEquals(List.of(1L, 1L, 1L), esDsm.getKitRequestShipping().stream().map(KitRequestShipping::getDdpInstanceId)
                .collect(Collectors.toList()));

    }
}