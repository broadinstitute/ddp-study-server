package org.broadinstitute.dsm.db.dao.mercury;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class MercuryOrderDao implements Dao<MercuryOrderDto> {
    public static String SQL_INSERT_MERCURY_ORDER = "insert into mercury_sequencing (order_id, order_date, ddp_participant_id, "
            + "kit_type_id, barcode, ddp_instance_id, created_by) values (?, ?, ?, ?, ?, ?, ?)";

    public static String SQL_GET_KIT_FROM_BARCODE_KIT_LABEL =
            "SELECT p.ddp_participant_id, kit_type_name, ktype.kit_type_id,  ddp.ddp_instance_id, kit.kit_label, ddp.mercury_order_creator "
                    + "FROM  ddp_participant as p "
                    + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
                    + "LEFT JOIN ddp_kit_request req on (req.ddp_participant_id = p.ddp_participant_id) "
                    + "LEFT JOIN kit_type ktype on ( req.kit_type_id = ktype.kit_type_id) "
                    + "LEFT JOIN ddp_kit kit on (req.dsm_kit_request_id = kit.dsm_kit_request_id) "
                    + "WHERE  p.ddp_participant_id = ?";

    public static String SQL_GET_KIT_FROM_BARCODE_SM_ID =
            "SELECT p.ddp_participant_id, kit_type_name, ktype.kit_type_id,  ddp.ddp_instance_id ,sm.sm_id_value, "
                    + "ddp.mercury_order_creator FROM  ddp_participant as p "
                    + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
                    + "LEFT JOIN ddp_institution inst on  (inst.participant_id = p.participant_id) "
                    + "LEFT JOIN ddp_medical_record mr on (mr.institution_id = inst.institution_id AND NOT mr.deleted <=> 1) "
                    + "LEFT JOIN ddp_onc_history_detail oD on (mr.medical_record_id = oD.medical_record_id AND NOT oD.deleted <=> 1) "
                    + "LEFT JOIN ddp_tissue t on (oD.onc_history_detail_id = t.onc_history_detail_id AND NOT t.deleted <=> 1) "
                    + "LEFT JOIN sm_id sm on (t.tissue_id  = sm.tissue_id AND NOT sm.deleted <=> 1)"
                    + "LEFT JOIN sm_id_type smt on (smt.sm_id_type_id = sm.sm_id_type_id ) "
                    + "LEFT JOIN kit_type ktype on ( smt.kit_type_id = ktype.kit_type_id) "
                    + "WHERE p.ddp_participant_id = ? "
                    + "AND NOT sm.sm_id_value IS NULL";

    public static String SQL_SELECT_ORDER_NUMBER = "Select * from mercury_sequencing where order_id = ?";

    public HashMap<String, MercuryOrderDto> getPossibleBarcodesForParticipant(String ddpParticipantId) {
        HashMap<String, MercuryOrderDto> map = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_KIT_FROM_BARCODE_KIT_LABEL)) {
                stmt.setString(1, ddpParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        MercuryOrderDto mercuryOrderDto = new MercuryOrderDto(rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getString(DBConstants.MERCURY_ORDER_CREATOR), rs.getString(DBConstants.KIT_LABEL),
                                rs.getInt("ktype." + DBConstants.KIT_TYPE_ID), rs.getInt(DBConstants.DDP_INSTANCE_ID));
                        log.info("found related info about barcode " + mercuryOrderDto.getBarcode());
                        map.put(mercuryOrderDto.getBarcode(), mercuryOrderDto);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error getting possible mercury orders for participant " + ddpParticipantId, e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting possible mercury orders for participant " + ddpParticipantId,
                    results.resultException);
        }

        results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_KIT_FROM_BARCODE_SM_ID)) {
                stmt.setString(1, ddpParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        MercuryOrderDto mercuryOrderDto = new MercuryOrderDto(rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getString(DBConstants.MERCURY_ORDER_CREATOR), rs.getString(DBConstants.SM_ID_VALUE),
                                rs.getInt("ktype." + DBConstants.KIT_TYPE_ID), rs.getInt(DBConstants.DDP_INSTANCE_ID));
                        log.info("found related info about barcode " + mercuryOrderDto.getBarcode());
                        map.put(mercuryOrderDto.getBarcode(), mercuryOrderDto);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error getting possible mercury orders for participant " + ddpParticipantId, e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            return dbVals;
        });
        log.info(String.format("Found %d possible barcodes for participant %s", map.size(), ddpParticipantId));
        return map;
    }

    public SimpleResult create(MercuryOrderDto mercuryOrderDto, Connection conn) {
        SimpleResult execResult = new SimpleResult();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_MERCURY_ORDER, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, mercuryOrderDto.getOrderId());
            stmt.setLong(2, mercuryOrderDto.getOrderDate());
            stmt.setString(3, mercuryOrderDto.getDdpParticipantId());
            stmt.setInt(4, mercuryOrderDto.getKitTypeId());
            stmt.setString(5, mercuryOrderDto.getBarcode());
            stmt.setInt(6, mercuryOrderDto.getDdpInstanceId());
            stmt.setString(7, mercuryOrderDto.getCreatedBy().orElse(""));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    execResult.resultValue = rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            execResult.resultException = ex;
        }
        return execResult;

    }

    @Override
    public int create(MercuryOrderDto mercuryOrderDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<MercuryOrderDto> get(long id) {
        return Optional.empty();
    }

    public boolean orderNumberExists(String orderNumber) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement selectKitRequest = conn.prepareStatement(SQL_SELECT_ORDER_NUMBER)) {
                selectKitRequest.setString(1, orderNumber);
                try (ResultSet rs = selectKitRequest.executeQuery();) {
                    if (rs.next()) {
                        dbVals.resultValue = true;
                    } else {
                        dbVals.resultValue = false;
                    }

                } catch (Exception e) {
                    throw new RuntimeException("Error getting values from db", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error checking if values exist in db", results.resultException);
        }
        return (boolean) results.resultValue;
    }

    public void insertMercuryOrders(List<MercuryOrderDto> newOrders) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult result = new SimpleResult();
            try {
                for (MercuryOrderDto order : newOrders) {
                    result = create(order, conn);
                    if (result.resultException != null) {
                        return result;
                    }
                }

            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            }
            return result;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error inserting new order ", results.resultException);
        }
    }
}
