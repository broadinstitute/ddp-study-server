package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.kit.ClinicalKitDto;
import org.broadinstitute.dsm.model.elastic.ESProfile;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BSPDummyKitDao implements Dao<ClinicalKitDto> {

    private static final String SQL_UPDATE_DUMMY_KIT = "UPDATE ddp_kit SET kit_label = ? where dsm_kit_request_id = ?";
    private static final String SQL_SELECT_RANDOM_PT = "SELECT ddp_participant_id FROM ddp_kit_request where ddp_instance_id = ?  ORDER "
            + "BY RAND() LIMIT 1";
    private static final Logger logger = LoggerFactory.getLogger(BSPDummyKitDao.class);

    public void updateKitLabel(String kitLabel, String dsmKitRequestId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_DUMMY_KIT)) {
                stmt.setString(1, kitLabel);
                stmt.setString(2, dsmKitRequestId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Updated dummy kit, set KitLabel " + kitLabel + " for kit with dsmKitRequestId " + dsmKitRequestId);
                } else {
                    throw new RuntimeException("Error updating kit  label for " + dsmKitRequestId + " updated " + result + " rows");
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error updating kit  label for " + dsmKitRequestId, results.resultException);
        }
    }

    public String getRandomParticipantForStudy(DDPInstance ddpInstance) {
        String ddpParticipantId = new BSPDummyKitDao().getRandomParticipantIdForStudy(ddpInstance.getDdpInstanceId()).orElseThrow(() -> {
            throw new RuntimeException("Random participant id was not generated");
        });
        Optional<ElasticSearchParticipantDto> maybeParticipantByParticipantId =
                ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstance.getParticipantIndexES(), ddpParticipantId);
        while (maybeParticipantByParticipantId.isEmpty() || maybeParticipantByParticipantId.get().getProfile().map(ESProfile::getHruid).isEmpty()) {
            ddpParticipantId = new BSPDummyKitDao().getRandomParticipantIdForStudy(ddpInstance.getDdpInstanceId()).orElseThrow(() -> {
                throw new RuntimeException("Random participant id was not generated");
            });
            maybeParticipantByParticipantId = ElasticSearchUtil.getParticipantESDataByParticipantId(ddpInstance.getParticipantIndexES(),
                    ddpParticipantId);
        }
        return ddpParticipantId;
    }

    public Optional<String> getRandomParticipantIdForStudy(String ddpInstanceId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_RANDOM_PT)) {
                stmt.setString(1, ddpInstanceId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    dbVals.resultValue = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Problem getting a random participant id for instance id " + ddpInstanceId, results.resultException);
        }
        return Optional.ofNullable((String) results.resultValue);
    }

    @Override
    public int create(ClinicalKitDto clinicalKitDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<ClinicalKitDto> get(long id) {
        return Optional.empty();
    }
}
