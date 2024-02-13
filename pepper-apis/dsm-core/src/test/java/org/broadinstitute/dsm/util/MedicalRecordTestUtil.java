package org.broadinstitute.dsm.util;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_CREATED;
import static org.broadinstitute.dsm.statics.ESObjectConstants.ONC_HISTORY_ID;
import static org.broadinstitute.dsm.statics.ESObjectConstants.PARTICIPANT_ID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.institution.DDPInstitutionDao;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.MedicalRecordDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.service.onchistory.OncHistoryElasticUpdater;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.lddp.handlers.util.Institution;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;
import org.junit.Assert;

/**
 * Utility class for creating medical records and onc history details for testing.
 * This class manages the records created in the DB. They are cleaned up by calling the tearDown method.
 */
@Slf4j
public class MedicalRecordTestUtil {
    private final Map<Integer, List<Integer>> participantToMedicalRecordId = new HashMap<>();
    private final Map<Integer, List<Integer>> participantToOncHistoryDetailId = new HashMap<>();
    private final Map<Integer, Integer> participantToOncHistoryId = new HashMap<>();
    private final List<Integer> participantBundleIds = new ArrayList<>();
    private int bundleCounter;

    public MedicalRecordTestUtil() {
        bundleCounter = 0;
    }

    /**
     * Clean up the records created by the methods in this class
     * Call this on test tearDown.
     */
    public void tearDown() {
        try {
            participantToOncHistoryId.values().forEach(MedicalRecordTestUtil::deleteOncHistory);
            participantToOncHistoryId.clear();

            participantToOncHistoryDetailId.values().forEach(ids ->
                    ids.forEach(MedicalRecordTestUtil::deleteOncHistoryDetail));
            participantToOncHistoryDetailId.clear();

            participantToMedicalRecordId.values().forEach(ids ->
                    ids.forEach(MedicalRecordTestUtil::deleteMedicalRecord));
            participantToMedicalRecordId.clear();

            participantBundleIds.forEach(MedicalRecordTestUtil::deleteMedicalRecordBundle);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Error in tearDown " + e.toString());
        }
    }

    /**
     * Creates a medical record and institution for a participant
     *
     * @return medical record id
     */
    public int createMedicalRecord(ParticipantDto participant, DDPInstanceDto instanceDto) {
        int medicalId = OncHistoryDetail.verifyOrCreateMedicalRecord(participant.getRequiredParticipantId(),
                participant.getRequiredDdpParticipantId(), instanceDto.getInstanceName(), true);
        addMedicalRecord(participant.getRequiredParticipantId(), medicalId);
        return medicalId;
    }

    public static void deleteMedicalRecord(int medicalRecordId) {
        MedicalRecordDao medicalRecordDao = new MedicalRecordDao();
        MedicalRecord medicalRecord = medicalRecordDao.get(medicalRecordId).get();
        medicalRecordDao.delete(medicalRecordId);
        DDPInstitutionDao ddpInstitutionDao = new DDPInstitutionDao();
        ddpInstitutionDao.delete(medicalRecord.getInstitutionId());
    }

    /**
     * Creates a medical record bundle for a participant that includes an Institution, MedicalRecord,
     * OncHistory and ParticipantRecords.
     *
     * @return medical record id
     */
    public int createMedicalRecordBundle(ParticipantDto participantDto, DDPInstanceDto ddpInstanceDto) {
        bundleCounter++;
        String ddpParticipantId = participantDto.getRequiredDdpParticipantId();
        String ddpInstitutionId = String.format("%s_%d_GUID", ddpParticipantId, bundleCounter);
        Institution institution = new Institution(ddpInstitutionId, "PHYSICIAN");
        String lastUpdated = Long.toString(System.currentTimeMillis());

        InstitutionRequest institutionRequest =
                new InstitutionRequest(bundleCounter, ddpParticipantId, List.of(institution), lastUpdated);
        List<Integer> medicalRecordIds = inTransaction(conn ->
                DDPMedicalRecordDataRequest.writeInstitutionBundle(conn, ddpInstanceDto.getDdpInstanceId().toString(),
                        institutionRequest, ddpInstanceDto.getInstanceName()));
        participantBundleIds.add(participantDto.getRequiredParticipantId());

        Assert.assertEquals(1, medicalRecordIds.size());
        return medicalRecordIds.get(0);
    }

    private static void deleteMedicalRecordBundle(int participantId) {
        Optional<OncHistoryDto> oncHistory = OncHistoryDao.getByParticipantId(participantId);
        oncHistory.ifPresent(oncHistoryDto -> MedicalRecordTestUtil.deleteOncHistory(oncHistoryDto.getOncHistoryId()));

        List<MedicalRecord> medRecords = MedicalRecord.getMedicalRecordsForParticipant(participantId);
        medRecords.forEach(medRecord -> MedicalRecordTestUtil.deleteMedicalRecord(medRecord.getMedicalRecordId()));

        ParticipantRecordDao participantRecordDao = new ParticipantRecordDao();
        Optional<ParticipantRecordDto> recordDto = participantRecordDao
                .getParticipantRecordByParticipantId(participantId);
        recordDto.ifPresent(participantRecordDto -> participantRecordDao
                .delete(participantRecordDto.getParticipantRecordId().orElseThrow()));
    }

    public int createOncHistoryDetail(ParticipantDto participant, OncHistoryDetail rec, String esIndex) {
        int oncHistoryDetailId = OncHistoryDetail.createOncHistoryDetail(rec);
        rec.setOncHistoryDetailId(oncHistoryDetailId);
        ElasticTestUtil.createOncHistoryDetail(esIndex, rec, participant.getRequiredDdpParticipantId());
        addOncHistoryDetail(participant.getRequiredParticipantId(), oncHistoryDetailId);
        return oncHistoryDetailId;
    }

    public static void deleteOncHistoryDetail(int oncHistoryDetailId) {
        OncHistoryDetailDaoImpl dao = new OncHistoryDetailDaoImpl();
        dao.delete(oncHistoryDetailId);
    }

    public int createOncHistory(ParticipantDto participantDto, String userId, String esIndex) {
        try {
            int participantId = participantDto.getRequiredParticipantId();
            OncHistoryDto oncHistoryDto = new OncHistoryDto.Builder()
                    .withParticipantId(participantId)
                    .withChangedBy(userId)
                    .withLastChangedNow()
                    .withCreatedNow().build();
            OncHistoryDao oncHistoryDao = new OncHistoryDao();
            int oncHistoryId = oncHistoryDao.create(oncHistoryDto);
            oncHistoryDto.setOncHistoryId(oncHistoryId);

            Map<String, Object> oncHistory = new HashMap<>();
            oncHistory.put(PARTICIPANT_ID, participantId);
            oncHistory.put(ONC_HISTORY_ID, oncHistoryDto.getOncHistoryId());
            oncHistory.put(ONC_HISTORY_CREATED, oncHistoryDto.getCreated());

            Map<String, Object> parent = new HashMap<>();
            parent.put(ONC_HISTORY, oncHistory);
            Map<String, Object> update = Map.of(ESObjectConstants.DSM, parent);

            OncHistoryElasticUpdater elasticUpdater = new OncHistoryElasticUpdater(esIndex);
            elasticUpdater.update(update, participantDto.getRequiredDdpParticipantId());
            participantToOncHistoryId.put(participantId, oncHistoryId);
            return oncHistoryId;
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Error creating onHistoryDetail " + e.toString());
            return -1;
        }
    }

    public static void deleteOncHistory(int oncHistoryId) {
        OncHistoryDao oncHistoryDao = new OncHistoryDao();
        oncHistoryDao.delete(oncHistoryId);
    }

    /**
     * Returns the medical record ids for a participant that were created by this class.
     * This does not include medical records created by 'createMedicalRecordBundle'.
     */
    public List<Integer> getParticipantMedicalIds(int participantId) {
        List<Integer> ids = participantToMedicalRecordId.get(participantId);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids;
    }

    /**
     * Deletes all oncHistoryDetail records for a participant.
     */
    public static void deleteOncHistoryDetailRecords(int participantId) {
        List<MedicalRecord> medRecords = MedicalRecord.getMedicalRecordsForParticipant(participantId);
        for (MedicalRecord medRecord : medRecords) {
            List<OncHistoryDetailDto> oncHistoryDetailList =
                    OncHistoryDetail.getOncHistoryDetailByMedicalRecord(medRecord.getMedicalRecordId());
            log.info("Foound {} oncHistoryDetail records for med record {}", oncHistoryDetailList.size(),
                    medRecord.getMedicalRecordId());
            OncHistoryDetailDaoImpl oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
            for (var ohd : oncHistoryDetailList) {
                oncHistoryDetailDao.delete((Integer) ohd.getColumnValues().get(DBConstants.ONC_HISTORY_DETAIL_ID));
            }
        }
    }

    /**
     * Returns the onc history detail ids for a participant that were created by this class.
     */
    public List<Integer> getParticipantOncHistoryDetailIds(int participantId) {
        List<Integer> ids = participantToOncHistoryDetailId.get(participantId);
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids;
    }

    /**
     * Returns the onc history id for a participant that was created by this class.
     * This does not include onc history created by 'createMedicalRecordBundle'.
     */
    public Integer getParticipantOncHistoryId(int participantId) {
        return participantToOncHistoryId.get(participantId);
    }

    private void addMedicalRecord(int participantId, int medicalRecordId) {
        if (participantToMedicalRecordId.containsKey(participantId)) {
            participantToMedicalRecordId.get(participantId).add(medicalRecordId);
            return;
        }
        participantToMedicalRecordId.put(participantId, new ArrayList<>(List.of(medicalRecordId)));
    }

    private void addOncHistoryDetail(int participantId, int oncHistoryDetailId) {
        if (participantToOncHistoryDetailId.containsKey(participantId)) {
            participantToOncHistoryDetailId.get(participantId).add(oncHistoryDetailId);
            return;
        }
        participantToOncHistoryDetailId.put(participantId, new ArrayList<>(List.of(oncHistoryDetailId)));
    }
}
