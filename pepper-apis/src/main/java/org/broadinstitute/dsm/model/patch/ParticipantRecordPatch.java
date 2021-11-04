package org.broadinstitute.dsm.model.patch;

import java.util.Optional;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.NameValue;

public class ParticipantRecordPatch extends BasePatch {

    private int participantId;

    public ParticipantRecordPatch(Patch patch) {
        super(patch);
    }
    
    @Override
    public Object doPatch() {
        return patchNameValuePair();
    }

    @Override
    protected Object patchNameValuePairs() {
        return null;
    }

    @Override
    protected Object patchNameValuePair() {
        prepare();
        Optional<Object> maybeResultMap = Optional.empty();
        if (participantId > 0) {
            maybeResultMap = processSingleNameValue();
        }
        return maybeResultMap.orElse(resultMap);
    }

    private void prepare() {
        participantId = insertDdpParticipant(patch, ddpInstance);
        insertDdpParticipantRecord(participantId);
    }

    private int insertDdpParticipant(Patch patch, DDPInstance ddpInstance) {
        ParticipantDto participantDto =
                new ParticipantDto.Builder(Integer.parseInt(ddpInstance.getDdpInstanceId()), System.currentTimeMillis())
                        .withDdpParticipantId(patch.getParentId())
                        .withLastVersion(0)
                        .withLastVersionDate("")
                        .withChangedBy(patch.getUser())
                        .build();
        return new ParticipantDao().create(participantDto);
    }

    private void insertDdpParticipantRecord(int participantId) {
        ParticipantRecordDto participantRecordDto =
                new ParticipantRecordDto.Builder(participantId, System.currentTimeMillis())
                        .withChangedBy("SYSTEM")
                        .builder();
        new ParticipantRecordDao().create(participantRecordDto);
    }

    @Override
    Object handleSingleNameValue() {
        Patch.patch(String.valueOf(participantId), patch.getUser(), patch.getNameValue(), dbElement);
        exportToESWithId(String.valueOf(participantId), patch.getNameValue());
        resultMap.put(PARTICIPANT_ID, String.valueOf(participantId));
        return resultMap;
    }

    @Override
    Optional<Object> processEachNameValue(NameValue nameValue) {
        return Optional.empty();
    }
}
