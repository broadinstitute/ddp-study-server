package org.broadinstitute.dsm.service.participant;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.dsm.util.export.ElasticSearchParticipantExporterFactory;
import org.broadinstitute.dsm.util.export.ParticipantExportPayload;

public class ParticipantService {
    private static final ParticipantDao participantDao = new ParticipantDao();

    /**
     * Create a participant and update ES
     *
     * @return ParticipantDto including the id of the created participant
     */
    public static ParticipantDto createParticipant(String ddpParticipantId, long lastVersion, String lastVersionDate,
                                                   DDPInstance ddpInstance, Connection conn) {
        ParticipantDto participantDto =
                new ParticipantDto.Builder(ddpInstance.getDdpInstanceIdAsInt())
                        .withDdpParticipantId(ddpParticipantId)
                        .withLastVersion(lastVersion)
                        .withLastVersionDate(lastVersionDate)
                        .withChangedBy(SystemUtil.SYSTEM).build();
        int id = participantDao.create(participantDto, conn);
        participantDto.setParticipantId(id);

        // TODO: Replace all this unnecessary complexity (ElasticSearchParticipantExporter etc.) -DC
        ElasticSearchParticipantExporterFactory.fromPayload(
                new ParticipantExportPayload(
                        id,
                        participantDto.getRequiredDdpParticipantId(),
                        ddpInstance.getDdpInstanceIdAsInt(),
                        ddpInstance.getName(),
                        // this is an invariant so no need to embellish the .orElseThrow()
                        DDPInstanceDao.of().getDDPInstanceByInstanceName(ddpInstance.getName()).orElseThrow()
                )
        ).export();
        return participantDto;
    }

    public static ParticipantDto createParticipant(String ddpParticipantId, long lastVersion, String lastVersionDate,
                                                   DDPInstance ddpInstance) {
        return inTransaction(conn ->
             createParticipant(ddpParticipantId, lastVersion, lastVersionDate, ddpInstance, conn)
        );
    }

    /**
     * Update participant last version number and last version date
     */
    public static void updateLastVersion(int participantId, long lastVersion, String lastVersionDate, Connection conn) {
        ParticipantDao.updateParticipantLastVersion(conn, participantId, lastVersion, lastVersionDate,
                SystemUtil.SYSTEM);
    }

    public static void updateLastVersion(int participantId, long lastVersion, String lastVersionDate) {
        ParticipantDao.updateParticipantLastVersion(participantId, lastVersion, lastVersionDate, SystemUtil.SYSTEM);
    }
}
