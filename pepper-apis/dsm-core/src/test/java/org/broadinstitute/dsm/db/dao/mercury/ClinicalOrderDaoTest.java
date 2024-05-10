package org.broadinstitute.dsm.db.dao.mercury;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dto.ddp.institution.DDPInstitutionDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.mercury.ClinicalOrderDto;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;
import org.broadinstitute.dsm.kits.TestKitUtil;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.broadinstitute.dsm.util.MercuryOrderTestUtil;
import org.broadinstitute.dsm.util.SampleIdTestUtil;
import org.broadinstitute.dsm.util.TissueTestUtil;
import org.broadinstitute.lddp.handlers.util.Institution;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.statics.DBConstants.KIT_SHIPPING;
import static org.broadinstitute.dsm.statics.DBConstants.PT_LIST_VIEW;

@Slf4j
public class ClinicalOrderDaoTest extends DbTxnBaseTest {

    private static final String TEST_USER = "testuser";

    private static final TissueTestUtil tissueTestUtil = new TissueTestUtil();

    private static final SampleIdTestUtil sampleIdTestUtil = new SampleIdTestUtil();

    private static final MercuryOrderTestUtil mercuryOrderTestUtil = new MercuryOrderTestUtil();

    private static final DDPInstitutionDao institutionDao = new DDPInstitutionDao();

    private static int medicalRecordId = -1;

    private static final ClinicalOrderDao clinicalOrderDao = new ClinicalOrderDao();

    private static final ParticipantDao participantDao = new ParticipantDao();

    private static final String ddpParticipantId = "ORDERTEST." + UUID.randomUUID();

    private static int userId;

    private static int participantId = -1;

    private static String studyInstanceName;

    private static DDPInstitutionDto institutionDto;

    private static int oncHistoryDetailId = -1;

    public static TestKitUtil testKitUtil;

    private static int ddpInstanceId = -1;

    private static String generateUserEmail() {
        return "ClinicalOrderDaoTest-" + System.currentTimeMillis() + "@broad.dev";
    }

    @BeforeClass
    /**
     * Create new study, participant, medical record, and onc history
     */
    public static void setup() {
        String nameAppend = "." + System.currentTimeMillis();
        studyInstanceName = "ClinOrdTest" + nameAppend;
        String studyGroup = "ClinOrdTest" + nameAppend;

        testKitUtil = new TestKitUtil(studyInstanceName, studyInstanceName, "CinOrdTest", studyGroup,
                "SALIVA", null);
        testKitUtil.setupInstanceAndSettings();
        userId = testKitUtil.adminUtil.createTestUser(generateUserEmail(), Arrays.asList(KIT_SHIPPING, PT_LIST_VIEW));

        ParticipantDto participantDto =
                new ParticipantDto.Builder(testKitUtil.ddpInstanceId, System.currentTimeMillis())
                        .withDdpParticipantId(ddpParticipantId)
                        .withLastVersion(0L)
                        .withLastVersionDate("")
                        .withChangedBy("SYSTEM")
                        .build();

        participantId = participantDao.create(participantDto);
        participantDto.setParticipantId(participantId);
        log.info("Created participant {}", participantDto.getParticipantId().get());

        institutionDto = new DDPInstitutionDto.Builder()
                .withDdpInstitutionId("testinstitute")
                .withLastChanged(System.currentTimeMillis())
                .withType("TESTING")
                .withParticipantId(participantId).build();
        int institutionId = institutionDao.create(institutionDto);
        institutionDto.setInstitutionId(institutionId);

        log.info("Created institution {}", institutionDto.getInstitutionId());

        inTransaction(conn -> {
            medicalRecordId = MedicalRecordUtil.insertMedicalRecord(conn,
                    Integer.toString(institutionDto.getInstitutionId()), ddpParticipantId, studyInstanceName,
                    new Institution(institutionDto.getDdpInstitutionId(), institutionDto.getType()), false);
            log.info("Created medical record {}", medicalRecordId);

            return null;
        });
        oncHistoryDetailId = OncHistoryDetail.createOncHistoryDetail(medicalRecordId, "tester");
        log.info("Created onc history detail {}", oncHistoryDetailId);
        ddpInstanceId = testKitUtil.adminUtil.getDdpInstanceId();
    }

    @Test
    public void testGetClinicalOrdersForTissuesWhenThereAreNoOrders() {
        List<Integer> tissueIds = new ArrayList<>();
        int tissueId = tissueTestUtil.createTissue(oncHistoryDetailId, TEST_USER);
        tissueIds.add(tissueId);

        String sample1Id = "NO_ORDERS";
        int smId = sampleIdTestUtil.createSampleForTissue(tissueId, ddpParticipantId, SmId.USS, sample1Id);
        log.info("Created testing sample {} with id {}", sample1Id, smId);

        Map<Integer, Collection<ClinicalOrderDto>> ordersByTissue =
                clinicalOrderDao.getClinicalOrdersForTissueIds(tissueIds);

        Assert.assertEquals(1, ordersByTissue.size());
        Assert.assertEquals(tissueId, ordersByTissue.keySet().iterator().next().intValue());
        Assert.assertTrue(ordersByTissue.values().iterator().next().isEmpty());
    }

    @Test
    public void testGetClinicalOrdersForTissuesWhenATissueHasOrders() {
        List<Integer> tissueIds = new ArrayList<>();
        int tissueId = tissueTestUtil.createTissue(oncHistoryDetailId, TEST_USER);
        tissueIds.add(tissueId);

        String sample1Id = "SM-BLAHBLAH";
        int smId = sampleIdTestUtil.createSampleForTissue(tissueId, ddpParticipantId, SmId.USS, sample1Id);

        log.info("Created testing sample {} with id {}", sample1Id, smId);

        String order1Barcode = "testtestBarcode";

        MercuryOrderDto orderDto = mercuryOrderTestUtil.createMercuryOrder(ddpParticipantId, order1Barcode,
                testKitUtil.kitTypeId, ddpInstanceId, tissueId);

        Map<Integer, Collection<ClinicalOrderDto>> ordersByTissue =
                clinicalOrderDao.getClinicalOrdersForTissueIds(tissueIds);

        Assert.assertEquals(1, ordersByTissue.size());
        Assert.assertEquals(1, ordersByTissue.get(tissueId).size());
        Assert.assertEquals(orderDto.getOrderId(), ordersByTissue.get(tissueId).iterator().next().getOrderId());

    }

    @Test
    public void testGetClinicalOrdersForTissuesWhenSomeTissuesHaveOrdersAndSomeDoNot() {
        List<Integer> tissueIds = new ArrayList<>();
        int tissueWithOrder = tissueTestUtil.createTissue(oncHistoryDetailId, TEST_USER);
        tissueIds.add(tissueWithOrder);

        String sampleWithOrder = "SM_ORDER1";
        int smId = sampleIdTestUtil.createSampleForTissue(tissueWithOrder, ddpParticipantId, SmId.USS, sampleWithOrder);
        log.info("Created testing sample {} with id {}", sampleWithOrder, smId);

        String order1Barcode = "TestBarcode1";

        ddpInstanceId = testKitUtil.adminUtil.getDdpInstanceId();
        MercuryOrderDto orderDto = mercuryOrderTestUtil.createMercuryOrder(ddpParticipantId, order1Barcode,
                testKitUtil.kitTypeId, ddpInstanceId, tissueWithOrder);

        log.info("Created order {} for tissue {} and sample {}", orderDto.getMercurySequencingId(), tissueWithOrder,
                sampleWithOrder);

        String order2Barcode = "TestBarcode2";
        MercuryOrderDto order2Dto = mercuryOrderTestUtil.createMercuryOrder(ddpParticipantId, order2Barcode,
                testKitUtil.kitTypeId, ddpInstanceId, tissueWithOrder);
        log.info("Created order {} for tissue {} and sample {}", order2Dto.getMercurySequencingId(), tissueWithOrder,
                sampleWithOrder);

        int tissueWithoutOrder = tissueTestUtil.createTissue(oncHistoryDetailId, TEST_USER);
        tissueIds.add(tissueWithoutOrder);

        String sampleWithoutOrder = "SM_NO_ORDER";
        int smIdWithoutOrder = sampleIdTestUtil.createSampleForTissue(tissueWithoutOrder, ddpParticipantId, SmId.USS,
                sampleWithoutOrder);
        log.info("Created testing sample {} with id {}", smIdWithoutOrder, smIdWithoutOrder);

        Map<Integer, Collection<ClinicalOrderDto>> ordersByTissue =
                clinicalOrderDao.getClinicalOrdersForTissueIds(tissueIds);

        Assert.assertEquals(2, ordersByTissue.size());
        Assert.assertEquals(2, ordersByTissue.get(tissueWithOrder).size());

        boolean foundOrder1 = false;
        boolean foundOrder2 = false;
        for (ClinicalOrderDto order : ordersByTissue.get(tissueWithOrder)) {
            if (order.getMercurySequencingId() == orderDto.getMercurySequencingId()) {
                foundOrder1 = true;
            }
            if (order.getMercurySequencingId() == order2Dto.getMercurySequencingId()) {
                foundOrder2 = true;
            }
        }
        Assert.assertTrue(foundOrder1);
        Assert.assertTrue(foundOrder2);
        Assert.assertTrue(ordersByTissue.get(tissueWithoutOrder).isEmpty());

    }

    @AfterClass
    public static void deleteTestingData() {
        mercuryOrderTestUtil.deleteCreatedOrders();
        sampleIdTestUtil.deleteCreatedSamples();
        tissueTestUtil.deleteCreatedTissues();
        OncHistoryDetail.delete(oncHistoryDetailId);
        log.info("Deleted onc history detail {} for medical record {}", oncHistoryDetailId, medicalRecordId);
        log.info("Deleting medical record {}", medicalRecordId);
        new MedicalRecordDao().delete(medicalRecordId);
        log.info("Deleted medical record {}", medicalRecordId);
        institutionDao.delete(institutionDto.getInstitutionId());
        log.info("Deleted institution {}", institutionDto.getInstitutionId());
        participantDao.delete(participantId);
        log.info("Deleted participant {}", participantId);
        testKitUtil.deleteGeneratedData();
    }
}
