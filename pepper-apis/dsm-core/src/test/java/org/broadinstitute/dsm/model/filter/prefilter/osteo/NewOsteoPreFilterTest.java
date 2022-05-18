package org.broadinstitute.dsm.model.filter.prefilter.osteo;

import org.broadinstitute.dsm.db.DDPInstance;
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

import static org.junit.Assert.assertEquals;

public class NewOsteoPreFilterTest {

    @Test
    public void filter() {

        var esDsm = new ESDsm();

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

        var esActivities = new ArrayList<>(List.of(
                new ESActivities("CONSENT", 12345L),
                new ESActivities("PREQUAL", 23456L),
                new ESActivities("ABOUT_YOU", 11111L)
        ));

        var esDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(esActivities)
                .withDsm(esDsm)
                .build();

        var ddpInstanceDto = new DDPInstanceDto.Builder()
                .withDdpInstanceId(1)
                .build();

        Optional<PreFilter> preFilter = PreFilter.fromPayload(PreFilterPayload.of(esDto, ddpInstanceDto));
        preFilter.ifPresent(PreFilter::filter);

        assertEquals(esDsm.getMedicalRecord().size(), 2);
        assertEquals(esDsm.getOn.size(), 2);

    }
}