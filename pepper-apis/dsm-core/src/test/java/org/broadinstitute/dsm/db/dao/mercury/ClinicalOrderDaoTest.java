package org.broadinstitute.dsm.db.dao.mercury;

import liquibase.pro.packaged.M;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeDao;
import org.broadinstitute.dsm.db.dao.kit.KitTypeImpl;
import org.broadinstitute.dsm.db.dto.ddp.institution.DDPInstitutionDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.kit.KitTypeDto;
import org.broadinstitute.dsm.db.dto.mercury.ClinicalOrderDto;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.juniperkits.TestKitUtil;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.broadinstitute.lddp.util.JsonTransformer;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;
import static org.broadinstitute.dsm.statics.DBConstants.KIT_SHIPPING;
import static org.broadinstitute.dsm.statics.DBConstants.PT_LIST_VIEW;

@Slf4j
public class ClinicalOrderDaoTest extends DbTxnBaseTest {

    private static final String TEST_USER = "testuser";

    private static final MedicalRecordDao mrDao = new MedicalRecordDao();

    private static final OncHistoryDao oncHistoryDao = new OncHistoryDao();

    private static final DDPInstitutionDao institutionDao = new DDPInstitutionDao();

    private static final Tissue tissueDao = new Tissue();

    private static int medicalRecordId = -1;

    private static final MercuryOrderDao mercuryOrderDao = new MercuryOrderDao();

    private static final ClinicalOrderDao clinicalOrderDao = new ClinicalOrderDao();

    private static final String ddpParticipantId = "ORDERTEST." + UUID.randomUUID();

    private static int userId;

    private static int participantId = -1;

    private static String studyInstanceName;

    private static DDPInstitutionDto institutionDto;

    private static int oncHistoryDetailId = -1;

    public static TestKitUtil testKitUtil;

    private static TissueSMIDDao smIdDao = new TissueSMIDDao();

    private static String generateUserEmail() {
        return "ClinicalOrderDaoTest-" + System.currentTimeMillis() + "@broad.dev";
    }

    @BeforeClass
    public static void setup() {
        String nameAppend = "." + System.currentTimeMillis();
        studyInstanceName = "instance" + nameAppend;
        String studyGroup = "ClinOrder" + nameAppend;

        testKitUtil = new TestKitUtil(studyInstanceName, studyInstanceName, "CinOrdTest", studyGroup, "SALIVA");
        testKitUtil.setupInstanceAndSettings();

        userId = TestKitUtil.adminUtil.createTestUser(generateUserEmail(), Arrays.asList(KIT_SHIPPING, PT_LIST_VIEW));

        ParticipantDto participantDto =
                new ParticipantDto.Builder(Integer.parseInt(TestKitUtil.ddpInstanceId), System.currentTimeMillis())
                        .withDdpParticipantId(ddpParticipantId)
                        .withLastVersion(0)
                        .withLastVersionDate("")
                        .withChangedBy("SYSTEM")
                        .build();

        participantId = new ParticipantDao().create(participantDto);
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
                    institutionDto.getDdpInstitutionId(), false);
            log.info("Created medical record {}", medicalRecordId);

            return null;
        });


        oncHistoryDetailId = OncHistoryDetail.createOncHistoryDetail(medicalRecordId, "tester");
        log.info("Created onc history detail {}", oncHistoryDetailId);

    }

    /*
    @Test
    public void testGetClinicalOrdersForTissuesWhenThereAreNoOrders() {
        Assert.fail("not written");

        String tissueId = tissueDao.createNewTissue(oncHistoryDto.getOncHistoryId())
    }
     */

    @Test
    public void testGetClinicalOrdersForTissuesWhenATissueHasOrders() {
        List<Integer> tissueIds = new ArrayList<>();

        // todo arz why do we pass in a string for onc history id when it's an int?
        Integer tissueId = Integer.parseInt(tissueDao.createNewTissue(Integer.toString(oncHistoryDetailId), TEST_USER));

        String sample1Id = "SM-BLAHBLAH";
        Integer smId = Integer.parseInt(
                smIdDao.createNewSMIDForTissue(tissueId.toString(), ddpParticipantId, SmId.USS, sample1Id));
        log.info("Created testing sample {} with id {}", sample1Id, smId);

        String order1Barcode = "testtestBarcode";

        MercuryOrderDto orderDto = new MercuryOrderDto(ddpParticipantId, ddpParticipantId, order1Barcode,
                Integer.parseInt(TestKitUtil.kitTypeId),
                TestKitUtil.adminUtil.getDdpInstanceId(), Long.valueOf(tissueId), null);
        orderDto.setOrderId(order1Barcode);
        tissueIds.add(mercuryOrderDao.create(orderDto, null));

        Map<Integer, Collection<ClinicalOrderDto>> ordersByTissue =
                clinicalOrderDao.getClinicalOrdersForTissueIds(tissueIds);

        Assert.assertEquals(1, ordersByTissue.size());
        Assert.assertEquals(1, ordersByTissue.get(tissueId).size());
        Assert.assertEquals(orderDto.getOrderId(), ordersByTissue.get(tissueId).iterator().next().getOrderId());

        String returnedJson = new JsonTransformer().render(ordersByTissue);

        String inputTissueIds = new JsonTransformer().render(tissueIds);
        log.info(inputTissueIds);
        log.info(returnedJson);
    }

    @Test  // todo arz make methods for creating new tissue and smids
    public void testGetClinicalOrdersForTissuesWhenSomeTissuesHaveOrdersAndSomeDoNot() {
        Assert.fail();
    }
    // todo OncHistoryDetailTestUtil, TissueTestUtil, MercuryOrderTestUtil, include tearDowns

    private OncHistoryDetail createNewOncHistoryForParticipant();

    private Tissue createNewSmIdAndTissueForOncHistory();

    public MercuryOrderDto createNewMercuryOrderForTissue();
}
