package org.broadinstitute.dsm.model.participant.data;

import java.util.Map;

import com.google.gson.Gson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.painless.AddToSingleScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;

@Slf4j
@Data
public class ParticipantRecord {
    private String ddpParticipantId;
    private int participantId;
    private int participantRecordId;
    private int ddpInstanceId;
    private String fieldTypeId;

    private ParticipantRecordDao participantRecordDao;
    private Map<String, String> data;


    public ParticipantRecord(String ddpParticipantId, int ddpInstanceId, ParticipantRecordDao participantRecordDao) {
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = ddpInstanceId;
        this.participantRecordDao = participantRecordDao;
    }

    public int createNewParticipantRecord() {
        participantId = insertDdpParticipant();
        participantRecordId = insertDdpParticipantRecord(participantId);
        return participantId;
    }

    private int insertDdpParticipant() {
        ParticipantDto participantDto =
                new ParticipantDto.Builder(ddpInstanceId, System.currentTimeMillis())
                        .withDdpParticipantId(ddpParticipantId)
                        .withLastVersion(0)
                        .withLastVersionDate("")
                        .withChangedBy("SYSTEM")
                        .build();
        return new ParticipantDao().create(participantDto);
    }

    private int insertDdpParticipantRecord(int participantId) {
        ParticipantRecordDto participantRecordDto =
                new ParticipantRecordDto.Builder(participantId, System.currentTimeMillis())
                        .withChangedBy("SYSTEM")
                        .build();
        return participantRecordDao.create(participantRecordDto);
    }

    public boolean insertDefaultValues(Map<String, String> data, int participantId, DDPInstance instance,
                                       ElasticSearchParticipantDto elasticSearchParticipantDto) {
        String additionalValues = new Gson().toJson(data);
        participantRecordDao.insertDefaultAdditionalValues(participantId, additionalValues);
        Participant participant = elasticSearchParticipantDto.getDsm().orElseThrow().getParticipant().orElse(new Participant());
        participant.setAdditionalValuesJson(additionalValues);
        DDPInstanceDto ddpInstanceDto =
                new DDPInstanceDao().getDDPInstanceByInstanceId(Integer.parseInt(instance.getDdpInstanceId())).orElseThrow();
        String participantGuid = Exportable.getParticipantGuid(ddpParticipantId, instance.getParticipantIndexES());
        try {
            UpsertPainlessFacade.of(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS, participant, ddpInstanceDto,
                    DBConstants.PARTICIPANT_ID, ESObjectConstants.DOC_ID,
                    participantGuid, new AddToSingleScriptBuilder())
                    .export();
        } catch (Exception e) {
            log.error(String.format("Error inserting participant record for guid: %s in ElasticSearch", participantGuid));
            e.printStackTrace();
            return false;
        }
        return true;
    }

}
