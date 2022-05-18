package org.broadinstitute.dsm.model.filter.prefilter.osteo;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.ESActivities;
import org.broadinstitute.dsm.model.elastic.ESDsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.model.filter.prefilter.PreFilter;
import org.broadinstitute.dsm.model.filter.prefilter.PreFilterPayload;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class NewOsteoPreFilterTest {

    @Test
    public void filter() {

        int ddpInstanceId = 1;
        String newOsteoInstanceName = "osteo2";
        String consentActivityCode = "CONSENT_ASSENT";

        ESDsm esDsm = new ESDsm();

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

        List<ESActivities> esActivities = new ArrayList<>(List.of(
                new ESActivities(consentActivityCode, 12345L),
                new ESActivities("PREQUAL", 23456L),
                new ESActivities("ABOUT_YOU", 11111L)
        ));

        ElasticSearchParticipantDto esDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(esActivities)
                .withDsm(esDsm)
                .build();

        DDPInstanceDto ddpInstanceDto = new DDPInstanceDto.Builder()
                .withInstanceName(newOsteoInstanceName)
                .withDdpInstanceId(ddpInstanceId)
                .build();

        Optional<PreFilter> preFilter = PreFilter.fromPayload(PreFilterPayload.of(esDto, ddpInstanceDto));
        preFilter.ifPresent(PreFilter::filter);

        assertEquals(2, esDsm.getMedicalRecord().size());
        assertEquals(List.of(1L, 1L), esDsm.getMedicalRecord().stream().map(MedicalRecord::getDdpInstanceId).collect(Collectors.toList()));

        assertEquals(1, esDsm.getOncHistoryDetail().size());
        assertEquals(List.of(1L), esDsm.getOncHistoryDetail().stream().map(OncHistoryDetail::getDdpInstanceId).collect(Collectors.toList()));

        assertEquals(3, esDsm.getKitRequestShipping().size());
        assertEquals(List.of(1L, 1L, 1L), esDsm.getKitRequestShipping().stream().map(KitRequestShipping::getDdpInstanceId).collect(Collectors.toList()));

        assertEquals(1, esDto.getActivities().size());
        assertEquals("PREQUAL", esDto.getActivities().stream().map(ESActivities::getActivityCode).findFirst().orElse("SomeOtherActivityCode"));

    }
}