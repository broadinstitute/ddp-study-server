package org.broadinstitute.dsm.db.dao.mercury;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class MercuryOrderDao implements Dao<MercuryOrderDto> {
    public static String SQL_INSERT_MERCURY_ORDER = "insert into mercury_sequencing (order_id, order_date, ddp_participant_id, "
            + "creator_id, kit_type_id, barcode) values (?, ?, ?, ?, ?, ?)";

    public static String SQL_GET_KIT_FROM_BARCODE =
            "SELECT p.ddp_participant_id, accession_number, ddp.instance_name, t.collaborator_sample_id,  "
                    + "kit_type_name, ktype.kit_type_id, research_project, bsp_material_type, bsp_receptacle_type, ddp.ddp_instance_id "
                    + "FROM sm_id sm LEFT JOIN ddp_tissue t on (t.tissue_id  = sm.tissue_id) "
                    + "LEFT JOIN ddp_onc_history_detail oD on (oD.onc_history_detail_id = t.onc_history_detail_id) "
                    + "LEFT JOIN ddp_medical_record mr on (mr.medical_record_id = oD.medical_record_id) "
                    + "LEFT JOIN ddp_institution inst on  (mr.institution_id = inst.institution_id AND NOT mr.deleted <=> 1) "
                    + "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) "
                    + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
                    + "LEFT JOIN instance_settings as settings on (ddp.ddp_instance_id = settings.ddp_instance_id) "
                    + "LEFT JOIN sm_id_type sit on (sit.sm_id_type_id = sm.sm_id_type_id) "
                    + "LEFT JOIN kit_type ktype on ( sit.kit_type_id = ktype.kit_type_id) "
                    + "LEFT JOIN ddp_kit_request req on (req.ddp_participant_id = p.ddp_participant_id)"
                    + "LEFT JOIN ddp_kit kit on (req.dsm_kit_request_id = kit.dsm_kit_request_id)"
                    + "WHERE (sm.sm_id_value = ? or kit.kit_label= ?) and p.ddp_participant_id = ?";

    public static String SQL_SELECT_ORDER_NUMBER = "Select * from mercury_sequencing where order_number = ?";

    public Optional<MercuryOrderDto> getMercuryOrderFromSMIdOrKitLabel(String barcode, String ddpParticipantId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_KIT_FROM_BARCODE)) {
                stmt.setString(1, barcode);
                stmt.setString(2, barcode);
                stmt.setString(3, ddpParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        log.info("found related info about barcode " + barcode);
                        MercuryOrderDto mercuryOrderDto = new MercuryOrderDto(rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getString(DBConstants.CREATOR_ID), barcode, rs.getInt("ktype." + DBConstants.KIT_TYPE_ID));
                        dbVals.resultValue = mercuryOrderDto;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error getting info for barcode " + barcode, e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting info for barcode " + barcode, results.resultException);
        }
        return Optional.ofNullable((MercuryOrderDto) results.resultValue);
    }

    public SimpleResult create(MercuryOrderDto mercuryOrderDto, Connection conn) {
        SimpleResult execResult = new SimpleResult();
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_MERCURY_ORDER, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, mercuryOrderDto.getOrderId());
            stmt.setLong(2, mercuryOrderDto.getOrderDate());
            stmt.setString(3, mercuryOrderDto.getDdpParticipantId());
            stmt.setString(4, mercuryOrderDto.getCreatorId());
            stmt.setInt(5, mercuryOrderDto.getKitTypeId());
            stmt.setString(6, mercuryOrderDto.getBarcode());
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
}
