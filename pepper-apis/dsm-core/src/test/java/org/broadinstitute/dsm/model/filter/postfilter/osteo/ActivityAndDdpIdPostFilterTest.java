
package org.broadinstitute.dsm.model.filter.postfilter.osteo;

import static org.broadinstitute.dsm.model.filter.postfilter.osteo.ActivityAndDdpIdPostFilterStrategyTest.randomString;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.junit.Assert;
import org.junit.Test;

public class ActivityAndDdpIdPostFilterTest {
    private static final int OLD_OSTEO_ARTIFICIAL_INSTANCE_ID = 100;

    private final DDPInstanceDto ddpInstanceDto =
            new DDPInstanceDto.Builder()
                    .withDdpInstanceId(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID).build();
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
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
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
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
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
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(0, esPtDto.getActivities().size());
    }

    @Test
    public void someCorrectActivitiesWithSomeCorrectOncHistories() {
        Dsm esDsm = new Dsm();

        esDsm.setOncHistoryDetail(new ArrayList<>(List.of(
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID))));

        var esPtDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(List.of(
                        new Activities("FAKE_ACTIVITY", "v1"),
                        new Activities("LOVEDONE", "v1"),
                        new Activities("FAKE_ACTIVITY_2", "v2"),
                        new Activities("PREQUAL", "v2"),
                        new Activities("RELEASE_MINOR", "v1"),
                        new Activities("FAKE_ACTIVITY_3", "v1")
                )).withDsm(esDsm).build();

        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(2, esPtDto.getActivities().size());
        Assert.assertEquals(1, esPtDto.getDsm().get().getOncHistoryDetail().size());
    }

    @Test
    public void allApplicableOncHistories() {
        Dsm esDsm = new Dsm();

        esDsm.setOncHistoryDetail(new ArrayList<>(List.of(
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID),
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID),
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID))));

        var esPtDto = new ElasticSearchParticipantDto.Builder().withDsm(esDsm).build();
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(3, esPtDto.getDsm().get().getOncHistoryDetail().size());
    }

    @Test
    public void noApplicableOncHistories() {
        Dsm esDsm = new Dsm();

        esDsm.setOncHistoryDetail(new ArrayList<>(List.of(
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1))));

        var esPtDto = new ElasticSearchParticipantDto.Builder().withDsm(esDsm).build();
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(0, esPtDto.getDsm().get().getOncHistoryDetail().size());
    }

    @Test
    public void someApplicableOncHistories() {
        Dsm esDsm = new Dsm();

        esDsm.setOncHistoryDetail(new ArrayList<>(List.of(
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new OncHistoryDetail(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID))));

        var esPtDto = new ElasticSearchParticipantDto.Builder().withDsm(esDsm).build();
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(1, esPtDto.getDsm().get().getOncHistoryDetail().size());
    }

    @Test
    public void someApplicableMedicalRecords() {
        Dsm esDsm = new Dsm();

        esDsm.setMedicalRecord(new ArrayList<>(List.of(
                new MedicalRecord(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new MedicalRecord(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new MedicalRecord(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID))));

        var esPtDto = new ElasticSearchParticipantDto.Builder().withDsm(esDsm).build();
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(1, esPtDto.getDsm().get().getMedicalRecord().size());
    }

    @Test
    public void noApplicableMedicalRecords() {
        Dsm esDsm = new Dsm();

        esDsm.setMedicalRecord(new ArrayList<>(List.of(
                new MedicalRecord(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new MedicalRecord(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new MedicalRecord(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1))));

        var esPtDto = new ElasticSearchParticipantDto.Builder().withDsm(esDsm).build();
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(0, esPtDto.getDsm().get().getMedicalRecord().size());
    }

    @Test
    public void allApplicableMedicalRecords() {
        Dsm esDsm = new Dsm();

        esDsm.setMedicalRecord(new ArrayList<>(List.of(
                new MedicalRecord(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID),
                new MedicalRecord(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID),
                new MedicalRecord(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID))));

        var esPtDto = new ElasticSearchParticipantDto.Builder().withDsm(esDsm).build();
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(3, esPtDto.getDsm().get().getMedicalRecord().size());
    }

    @Test
    public void someApplicableKitRequestShipping() {
        Dsm esDsm = new Dsm();

        esDsm.setKitRequestShipping(new ArrayList<>(List.of(
                new KitRequestShipping(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new KitRequestShipping(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new KitRequestShipping(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID))));

        var esPtDto = new ElasticSearchParticipantDto.Builder().withDsm(esDsm).build();
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(1, esPtDto.getDsm().get().getKitRequestShipping().size());
    }

    @Test
    public void noApplicableKitRequestShipping() {
        Dsm esDsm = new Dsm();

        esDsm.setKitRequestShipping(new ArrayList<>(List.of(
                new KitRequestShipping(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new KitRequestShipping(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1),
                new KitRequestShipping(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID - 1))));

        var esPtDto = new ElasticSearchParticipantDto.Builder().withDsm(esDsm).build();
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(0, esPtDto.getDsm().get().getKitRequestShipping().size());
    }

    @Test
    public void allApplicableKitRequestShipping() {
        Dsm esDsm = new Dsm();

        esDsm.setKitRequestShipping(new ArrayList<>(List.of(
                new KitRequestShipping(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID),
                new KitRequestShipping(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID),
                new KitRequestShipping(OLD_OSTEO_ARTIFICIAL_INSTANCE_ID))));

        var esPtDto = new ElasticSearchParticipantDto.Builder().withDsm(esDsm).build();
        new ActivityAndDdpIdPostFilter(esPtDto, ddpInstanceDto).filter();
        Assert.assertEquals(3, esPtDto.getDsm().get().getKitRequestShipping().size());
    }
}
