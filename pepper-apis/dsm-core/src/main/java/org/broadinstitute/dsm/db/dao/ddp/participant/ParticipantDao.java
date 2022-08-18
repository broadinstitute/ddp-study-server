package org.broadinstitute.dsm.db.dao.ddp.participant;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;

public interface ParticipantDao extends Dao<ParticipantDto> {
    Optional<Integer> getParticipantIdByGuidAndDdpInstanceId(String guid, int ddpInstanceId);
}
