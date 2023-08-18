package org.broadinstitute.dsm.model.participant.data;

import java.util.Map;

import com.google.gson.Gson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.export.painless.AddToSingleScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.export.DefaultParticipantExporter;
import org.broadinstitute.dsm.util.export.ElasticSearchParticipantExporterFactory;
import org.broadinstitute.dsm.util.export.ParticipantExportPayload;

@Slf4j
@Data
public class ParticipantRecord {
    private String ddpParticipantId;
    private int participantId;
    private int participantRecordId;
    private DDPInstanceDto ddpInstanceDto;
    private String fieldTypeId;
    private ParticipantExportPayload participantExportPayload;
    private Participant participant;

    private ParticipantRecordDao participantRecordDao;
    private Map<String, String> data;


    public ParticipantRecord(String ddpParticipantId, DDPInstanceDto ddpInstanceDto, ParticipantRecordDao participantRecordDao) {
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceDto = ddpInstanceDto;
        this.participantRecordDao = participantRecordDao;
    }

    public int createNewParticipantRecord() {
        participantId = insertDdpParticipant();
        participantRecordId = insertDdpParticipantRecord(participantId);
        return participantId;
    }

    private int insertDdpParticipant() {
        ParticipantDto participantDto =
                new ParticipantDto.Builder(ddpInstanceDto.getDdpInstanceId(), System.currentTimeMillis())
                        .withDdpParticipantId(ddpParticipantId)
                        .withLastVersion(0)
                        .withLastVersionDate("")
                        .withChangedBy("SYSTEM")
                        .build();

        int participantId = new ParticipantDao().create(participantDto);
        participantExportPayload = new ParticipantExportPayload(
                participantId,
                ddpParticipantId,
                String.valueOf(ddpInstanceDto.getDdpInstanceId()),
                ddpInstanceDto.getInstanceName(),
                ddpInstanceDto
        );
        ElasticSearchParticipantExporterFactory.fromPayload(
                participantExportPayload
        ).export();

        return participantId;
    }

    private int insertDdpParticipantRecord(int participantId) {
        ParticipantRecordDto participantRecordDto =
                new ParticipantRecordDto.Builder(participantId, System.currentTimeMillis())
                        .withChangedBy("SYSTEM")
                        .build();
        return participantRecordDao.create(participantRecordDto);
    }

    public boolean insertDefaultValues(Map<String, String> data, int participantId) {
        String additionalValues = new Gson().toJson(data);
        participantRecordDao.insertDefaultAdditionalValues(participantId, additionalValues);
        participant = new DefaultParticipantExporter(participantExportPayload).buildParticipantFromPayload(participantExportPayload);
        participant.setAdditionalValuesJson(additionalValues);
        try {
            UpsertPainlessFacade.of(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS, participant, ddpInstanceDto,
                    DBConstants.PARTICIPANT_ID, ESObjectConstants.DOC_ID,
                    ddpParticipantId, new AddToSingleScriptBuilder())
                    .export();
        } catch (Exception e) {
            throw new DsmInternalError(String.format("Error inserting participant record for guid: %s in ElasticSearch",
                    ddpParticipantId));
        }
        return true;
    }

}
