package org.broadinstitute.dsm.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.medical.records.ESMedicalRecordsDao;
import org.broadinstitute.dsm.db.dto.medical.records.ESMedicalRecordsDto;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Map;

public class MedicalRecordExporter implements Exporter {

    private static final Logger logger = LoggerFactory.getLogger(MedicalRecordExporter.class);
    private static final ESMedicalRecordsDao esMedicalRecordsDao = new ESMedicalRecordsDao();
    private static final ObjectMapper oMapper = new ObjectMapper();

    @Override
    public void export(DDPInstance instance) {
        int instanceId = instance.getDdpInstanceIdAsInt();
        logger.info("Started exporting medical records for instance with id " + instanceId);
        ArrayDeque<ESMedicalRecordsDto> esMedicalRecords = new ArrayDeque<>(esMedicalRecordsDao.getESMedicalRecordsByInstanceId(instanceId));
        while (!esMedicalRecords.isEmpty()) {
            ESMedicalRecordsDto medicalRecord = esMedicalRecords.pop();
            Map<String, Object> map = oMapper.convertValue(medicalRecord, Map.class);
            if (medicalRecord.getMedicalRecordId() != null && medicalRecord.getDdpParticipantId() != null) {
                ElasticSearchUtil.writeDsmRecord(instance, medicalRecord.getMedicalRecordId(), medicalRecord.getDdpParticipantId(),
                        ESObjectConstants.MEDICAL_RECORDS, ESObjectConstants.MEDICAL_RECORDS_ID, map);
            }
        }
        logger.info("Finished exporting medical records for instance with id " + instanceId);
    }
}
