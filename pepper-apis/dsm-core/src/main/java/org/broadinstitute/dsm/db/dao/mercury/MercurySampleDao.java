package org.broadinstitute.dsm.db.dao.mercury;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dto.mercury.MercurySampleDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

public class MercurySampleDao implements Dao<MercurySampleDto> {
    public static String SQL_GET_ELIGIBLE_TISSUES =
            "SELECT * FROM ("
                    + "SELECT collaborator_sample_id, t.sent_gp, oD.date_px, oD.tissue_received, t.tissue_id, "
                    + " (SELECT count(*) from sm_id sm  "
                    + "   left join sm_id_type smtype on (sm.sm_id_type_id = smtype.sm_id_type_id)  "
                    + "   where smtype.sm_id_type = \"uss\" and sm.tissue_id = t.tissue_id and NOT sm.deleted <=> 1 "
                    + "   and sm.received_date is not null) as count_of_uss, "
                    + " (SELECT count(*) from sm_id sm  "
                    + "   left join sm_id_type smtype on (sm.sm_id_type_id = smtype.sm_id_type_id)  "
                    + "   where smtype.sm_id_type = \"scrolls\" and sm.tissue_id = t.tissue_id and NOT sm.deleted <=> 1 "
                    + "   and sm.received_date is not null) as count_of_scrolls "
                    + "FROM ddp_participant as p "
                    + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
                    + "LEFT JOIN ddp_institution inst on (inst.participant_id = p.participant_id) "
                    + "LEFT JOIN ddp_medical_record mr on (mr.institution_id = inst.institution_id AND NOT mr.deleted <=> 1) "
                    + "LEFT JOIN ddp_onc_history_detail oD on (mr.medical_record_id = oD.medical_record_id AND NOT oD.deleted <=> 1) "
                    + "LEFT JOIN ddp_tissue t on (oD.onc_history_detail_id = t.onc_history_detail_id AND NOT t.deleted <=> 1) "
                    + "WHERE oD.tissue_received IS NOT NULL AND p.ddp_participant_id = ? AND ddp.instance_name = ? "
                    + "AND  IFNULL(t.uss_count, -1) = (SELECT count(*) from sm_id sm "
                    + "left join sm_id_type smtype on (sm.sm_id_type_id = smtype.sm_id_type_id) "
                    + "where smtype.sm_id_type = \"uss\" and sm.tissue_id = t.tissue_id and NOT sm.deleted <=> 1 "
                    + "and sm.received_date is not null) "
                    + "AND IFNULL(t.scrolls_count, -1) = (SELECT count(*) from sm_id sm "
                    + "left join sm_id_type smtype on (sm.sm_id_type_id = smtype.sm_id_type_id) "
                    + "where smtype.sm_id_type = \"scrolls\" and sm.tissue_id = t.tissue_id and NOT sm.deleted <=> 1 "
                    + "and sm.received_date is not null) ) as table1 "
                    + "left join (select max(mercury_sequencing_id) AS ms_id, tissue_id as seqt "
                    + "From ddp_mercury_sequencing where ddp_participant_id = ? group by (tissue_id) ) as table2 "
                    + "on table2.seqt = table1.tissue_id "
                    + "LEFT JOIN (select order_id, mercury_sequencing_id, order_status, order_date, mercury_pdo_id "
                    + "FROM ddp_mercury_sequencing WHERE ddp_participant_id = ?) as table3 ON(table3.mercury_sequencing_id = table2.ms_id) "
                    + " WHERE (table1.count_of_scrolls != 0 OR table1.count_of_uss != 0) ";


    public static String SQL_GET_ELIGIBLE_SAMPLES =
            "SELECT * from ( "
                    + "SELECT req.ddp_kit_request_id, req.bsp_collaborator_sample_id, collection_date, req.dsm_kit_request_id, "
                    + "    kit.sequencing_restriction FROM ddp_kit_request req "
                    + "    LEFT JOIN ddp_kit kit on (req.dsm_kit_request_id = kit.dsm_kit_request_id) "
                    + "    LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = req.ddp_instance_id) "
                    + "    WHERE  req.ddp_participant_id = ? AND ddp.instance_name = ? AND kit.receive_date is not null) as table1  "
                    + "    LEFT JOIN (select max(mercury_sequencing_id) AS ms_id, dsm_kit_request_id "
                    + "    FROM ddp_mercury_sequencing WHERE ddp_participant_id = ? GROUP BY (dsm_kit_request_id) ) AS table2 "
                    + "    ON table2.dsm_kit_request_id = table1.dsm_kit_request_id "
                    + "    LEFT JOIN (select order_id, mercury_sequencing_id, order_date, order_status, mercury_pdo_id "
                    + "    FROM ddp_mercury_sequencing WHERE ddp_participant_id = ?) as table3 "
                    + "    ON(table3.mercury_sequencing_id = table2.ms_id) ";

    public static String TISSUE_SAMPLE_TYPE = "Tumor";
    public static String KIT_SAMPLE_TYPE = "Normal";
    public static String RECEIVED_STATUS = "Received";
    public static String TISSUE_SENT_GP_STATUS = "Sent to GP";

    @Override
    public int create(MercurySampleDto mercurySampleDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<MercurySampleDto> get(long id) {
        return Optional.empty();
    }

    public List<MercurySampleDto> findEligibleSamples(String ddpParticipantId, String realm,
                                                      KitDaoImpl kitDaoImpl) {
        ArrayList<MercurySampleDto> samples = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try {
                samples.addAll(getEligibleTissues(conn, ddpParticipantId, realm, kitDaoImpl));
                samples.addAll(getEligibleKits(conn, ddpParticipantId, realm));
            } catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of eligible samples for participant " + ddpParticipantId,
                    results.resultException);
        }
        return samples;
    }

    private List<MercurySampleDto> getEligibleKits(Connection conn, String ddpParticipantId, String realm) throws Exception {
        ArrayList<MercurySampleDto> samples = new ArrayList<>();
        SimpleResult dbVals = new SimpleResult();
        try (PreparedStatement statement = conn.prepareStatement(SQL_GET_ELIGIBLE_SAMPLES)) {
            statement.setString(1, ddpParticipantId);
            statement.setString(2, realm);
            statement.setString(3, ddpParticipantId);
            statement.setString(4, ddpParticipantId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    MercurySampleDto mercurySampleDto = new MercurySampleDto(KIT_SAMPLE_TYPE,
                            rs.getString(DBConstants.BSP_COLLABORATOR_SAMPLE_ID), RECEIVED_STATUS,
                            rs.getString(DBConstants.COLLECTION_DATE), rs.getLong(DBConstants.MERCURY_ORDER_DATE),
                            null, rs.getLong(DBConstants.DSM_KIT_REQUEST_ID),
                            rs.getString(DBConstants.SEQUENCING_RESTRICTION), rs.getString(DBConstants.MERCURY_ORDER_STATUS),
                            rs.getString(DBConstants.MERCURY_ORDER_ID), rs.getString(DBConstants.MERCURY_PDO_ID)
                    );
                    samples.add(mercurySampleDto);
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
        } catch (SQLException e) {
            dbVals.resultException = e;
        }
        if (dbVals.resultException != null) {
            throw dbVals.resultException;
        }
        return samples;
    }

    private List<MercurySampleDto> getEligibleTissues(Connection conn, String ddpParticipantId, String realm,
                                                      KitDaoImpl kitDaoImpl) throws Exception {
        ArrayList<MercurySampleDto> samples = new ArrayList<>();
        if (!participantHasKitReceived(conn, ddpParticipantId, kitDaoImpl)) {
            return samples;
        }
        SimpleResult dbVals = new SimpleResult();
        try (PreparedStatement statement = conn.prepareStatement(SQL_GET_ELIGIBLE_TISSUES)) {
            statement.setString(1, ddpParticipantId);
            statement.setString(2, realm);
            statement.setString(3, ddpParticipantId);
            statement.setString(4, ddpParticipantId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    MercurySampleDto mercurySampleDto = new MercurySampleDto(TISSUE_SAMPLE_TYPE,
                            rs.getString(DBConstants.COLLABORATOR_SAMPLE_ID), getSampleStatus(rs),
                            rs.getString(DBConstants.DATE_PX), rs.getLong(DBConstants.MERCURY_ORDER_DATE),
                            rs.getLong(DBConstants.TISSUE_ID), null,
                            null, rs.getString(DBConstants.MERCURY_ORDER_STATUS),
                            rs.getString(DBConstants.MERCURY_ORDER_ID), rs.getString(DBConstants.MERCURY_PDO_ID)
                    );
                    samples.add(mercurySampleDto);
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
        } catch (SQLException e) {
            dbVals.resultException = e;
        }
        if (dbVals.resultException != null) {
            throw dbVals.resultException;
        }
        return samples;
    }

    private boolean participantHasKitReceived(Connection conn, String ddpParticipantId, KitDaoImpl kitDaoImpl) {
        return kitDaoImpl.hasKitReceived(conn, ddpParticipantId);
    }

    private String getSampleStatus(ResultSet rs) throws SQLException {
        if (StringUtils.isNotBlank(rs.getString(DBConstants.SENT_GP))) {
            return TISSUE_SENT_GP_STATUS;
        }
        return RECEIVED_STATUS;
    }
}
