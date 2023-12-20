package org.broadinstitute.dsm.db.dao.mercury;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.ClinicalOrder;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.util.DaoUtil;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderDto;
import org.broadinstitute.dsm.db.dto.mercury.MercuryOrderUseCase;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.mercury.BaseMercuryStatusMessage;
import org.broadinstitute.dsm.model.mercury.MercuryStatusMessage;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class MercuryOrderDao implements Dao<MercuryOrderDto> {
    public static final String FAILED = "Failed";
    public static String SQL_INSERT_MERCURY_ORDER = "insert into ddp_mercury_sequencing (order_id, order_date, ddp_participant_id, "
            + "kit_type_id, barcode, ddp_instance_id, created_by, tissue_id, dsm_kit_request_id, order_message, status_message) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static String SQL_GET_KIT_FROM_BARCODE_KIT_LABEL =
            "SELECT p.ddp_participant_id, kit_type_name, ktype.kit_type_id,  ddp.ddp_instance_id, kit.kit_label, "
                    + " ddp.mercury_order_creator, kit.dsm_kit_request_id "
                    + "FROM  ddp_participant as p "
                    + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
                    + "LEFT JOIN ddp_kit_request req on (req.ddp_participant_id = p.ddp_participant_id) "
                    + "LEFT JOIN kit_type ktype on ( req.kit_type_id = ktype.kit_type_id) "
                    + "LEFT JOIN ddp_kit kit on (req.dsm_kit_request_id = kit.dsm_kit_request_id) "
                    + "WHERE  p.ddp_participant_id = ?";

    public static String SQL_GET_KIT_FROM_BARCODE_SM_ID =
            "SELECT p.ddp_participant_id, kit_type_name, ktype.kit_type_id,  ddp.ddp_instance_id ,sm.sm_id_value, "
                    + " ddp.mercury_order_creator, t.tissue_id "
                    + "FROM  ddp_participant as p "
                    + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
                    + "LEFT JOIN ddp_institution inst on  (inst.participant_id = p.participant_id) "
                    + "LEFT JOIN ddp_medical_record mr on (mr.institution_id = inst.institution_id AND NOT mr.deleted <=> 1) "
                    + "LEFT JOIN ddp_onc_history_detail oD on (mr.medical_record_id = oD.medical_record_id) "
                    + "LEFT JOIN ddp_tissue t on (oD.onc_history_detail_id = t.onc_history_detail_id) "
                    + "LEFT JOIN sm_id sm on (t.tissue_id  = sm.tissue_id) "
                    + "LEFT JOIN sm_id_type smt on (smt.sm_id_type_id = sm.sm_id_type_id ) "
                    + "LEFT JOIN kit_type ktype on ( smt.kit_type_id = ktype.kit_type_id) "
                    + "WHERE p.ddp_participant_id = ? "
                    + "AND NOT sm.sm_id_value IS NULL";

    public static String SQL_SELECT_ORDER_NUMBER = "Select * from ddp_mercury_sequencing where order_id = ?";

    public static String SQL_DELETE_ORDER = "delete from ddp_mercury_sequencing where mercury_sequencing_id = ?";

    public static String SQL_SELECT_LAST_UPDATED_ORDER =
            "Select min(mercury_sequencing_id) as mercury_sequencing_id, min(ddp_instance_id) as ddp_instance_id, min(ddp_participant_id) "
                    + "  as ddp_participant_id, min(order_date) as order_date, order_id, tissue_id, dsm_kit_request_id "
                    + "from ddp_mercury_sequencing "
                    + " where order_id =?  group by order_id, tissue_id, dsm_kit_request_id ";
    public static String SQL_UPDATE_ORDER_STATUS =
            "UPDATE ddp_mercury_sequencing SET order_status = ?, status_date = ?, mercury_pdo_id = ?,  "
                    + " status_detail = ?, status_message = ? where order_id = ?";

    private static final int MAX_CHAR_ALLOWED = 2000;

    public static void updateOrderStatus(BaseMercuryStatusMessage baseMercuryStatusMessage, String msgData) {
        long statusDate = System.currentTimeMillis();
        //check the length of fields to make sure they fit in the database
        String messageString = msgData.substring(0, Math.min(msgData.length(), MAX_CHAR_ALLOWED));
        String details = baseMercuryStatusMessage.getStatus().getDetails();
        String statusDetail = details.substring(0, Math.min(details.length(), MAX_CHAR_ALLOWED));
        AtomicReference<ClinicalOrder> clinicalOrderAtomicReference = new AtomicReference<>();
        SimpleResult results = TransactionWrapper.inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            MercuryStatusMessage mercuryStatusMessage = baseMercuryStatusMessage.getStatus();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_ORDER_STATUS)) {
                stmt.setString(1, mercuryStatusMessage.getOrderStatus());
                stmt.setLong(2, statusDate);
                stmt.setString(3, mercuryStatusMessage.getPdoKey());
                stmt.setString(4, statusDetail);
                stmt.setString(5, messageString);
                stmt.setString(6, mercuryStatusMessage.getOrderID());
                int result = stmt.executeUpdate();
                if (result != 0) {
                    log.info("Updated Mercury status for order id " + mercuryStatusMessage.getOrderID());
                    if (baseMercuryStatusMessage.getStatus().getOrderStatus().equals(FAILED)) {
                        log.error(String.format("Mercury rejected the sequencing order %s with order number %s",
                                baseMercuryStatusMessage.getStatus().getJson(), baseMercuryStatusMessage.getStatus().getOrderID()));
                    }
                } else {
                    dbVals.resultException = new DsmInternalError(
                            "Error updating Mercury status for order id " + mercuryStatusMessage.getOrderID()
                                    + " it was updating 0 rows");
                }
            } catch (SQLException ex) {
                dbVals.resultException =
                        new DsmInternalError(String.format(
                                "Error encountered while updating Mercury status for order id %s, status message received was %s",
                                        mercuryStatusMessage.getOrderID(), baseMercuryStatusMessage), ex);
                return dbVals;
            }
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_LAST_UPDATED_ORDER)) {
                stmt.setString(1, mercuryStatusMessage.getOrderID());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    clinicalOrderAtomicReference.set(new ClinicalOrder(rs.getString(DBConstants.MERCURY_SEQUENCING_ID),
                            rs.getString(DBConstants.MERCURY_ORDER_ID),
                            rs.getString(DBConstants.DDP_PARTICIPANT_ID), rs.getLong(DBConstants.MERCURY_ORDER_DATE),
                            rs.getLong(DBConstants.DDP_INSTANCE_ID),
                            null, statusDate, null,
                            rs.getLong(DBConstants.TISSUE_ID), rs.getLong(DBConstants.DSM_KIT_REQUEST_ID), null));
                } else {
                    dbVals.resultException = new DsmInternalError(String.format("Couldn't find an order based on the order id %s",
                            mercuryStatusMessage.getOrderID()));
                }
            } catch (SQLException ex) {
                dbVals.resultException =
                        new DsmInternalError(String.format(
                                "Couldn't find an order based on the order id %s, ES update will fail. Status message received was %s",
                                mercuryStatusMessage.getOrderID(), baseMercuryStatusMessage), ex);
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw (DsmInternalError) results.resultException;
        }
        MercuryOrderUseCase.exportStatusToES(baseMercuryStatusMessage, clinicalOrderAtomicReference.get(), statusDate);
    }

    public Map<String, MercuryOrderDto> getPossibleBarcodesForParticipant(String ddpParticipantId) {
        String errorMessage = "Error getting eligible barcodes for a mercury order for participant " + ddpParticipantId;
        HashMap<String, MercuryOrderDto> map = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_KIT_FROM_BARCODE_KIT_LABEL)) {
                stmt.setString(1, ddpParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        MercuryOrderDto mercuryOrderDto = new MercuryOrderDto(rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getString(DBConstants.MERCURY_ORDER_CREATOR), rs.getString(DBConstants.KIT_LABEL),
                                rs.getInt("ktype." + DBConstants.KIT_TYPE_ID), rs.getInt(DBConstants.DDP_INSTANCE_ID),
                                null, rs.getLong("kit." + DBConstants.DSM_KIT_REQUEST_ID));
                        log.info("found related info about barcode " + mercuryOrderDto.getBarcode());
                        map.put(mercuryOrderDto.getBarcode(), mercuryOrderDto);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError(errorMessage, results.resultException);
        }

        results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_KIT_FROM_BARCODE_SM_ID)) {
                stmt.setString(1, ddpParticipantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        MercuryOrderDto mercuryOrderDto = new MercuryOrderDto(rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getString(DBConstants.MERCURY_ORDER_CREATOR), rs.getString(DBConstants.SM_ID_VALUE),
                                rs.getInt("ktype." + DBConstants.KIT_TYPE_ID), rs.getInt(DBConstants.DDP_INSTANCE_ID),
                                rs.getLong(DBConstants.TISSUE_ID), null);
                        log.info("found related info about barcode " + mercuryOrderDto.getBarcode());
                        map.put(mercuryOrderDto.getBarcode(), mercuryOrderDto);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error getting the mercury orders", results.resultException);
        }
        log.info(String.format("Found %d possible barcodes for participant %s", map.size(), ddpParticipantId));
        return map;
    }

    @Override
    public int create(MercuryOrderDto mercuryOrderDto) {
        throw new IllegalStateException("Call the version of this method that includes the raw json payload");
    }

    public int create(MercuryOrderDto mercuryOrderDto, String messageJson) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_MERCURY_ORDER, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, mercuryOrderDto.getOrderId());
                stmt.setLong(2, mercuryOrderDto.getOrderDate());
                stmt.setString(3, mercuryOrderDto.getDdpParticipantId());
                stmt.setInt(4, mercuryOrderDto.getKitTypeId());
                stmt.setString(5, mercuryOrderDto.getBarcode());
                stmt.setInt(6, mercuryOrderDto.getDdpInstanceId());
                stmt.setString(7, mercuryOrderDto.getCreatedBy().orElse(""));
                stmt.setString(10, messageJson);
                stmt.setNull(11, Types.VARCHAR);
                if (mercuryOrderDto.getTissueId() != null) {
                    stmt.setString(8, String.valueOf(mercuryOrderDto.getTissueId()));
                } else {
                    stmt.setNull(8, Types.INTEGER);
                }
                if (mercuryOrderDto.getDsmKitRequestId() != null) {
                    stmt.setString(9, String.valueOf(mercuryOrderDto.getDsmKitRequestId()));
                } else {
                    stmt.setNull(9, Types.INTEGER);
                }

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

        });
        if (results.resultException != null) {
            throw new DsmInternalError(String.format("Error inserting mercury order for order with json {}", messageJson),
                    results.resultException);
        }
        return (int) results.resultValue;
    }

    @Override
    public int delete(int id) {
        DaoUtil.deleteSingleRowById(id, SQL_DELETE_ORDER);
        return 1;
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
                try (ResultSet rs = selectKitRequest.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = true;
                    } else {
                        dbVals.resultValue = false;
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError(String.format("Error checking if mercury order number %s exist in db", orderNumber),
                    results.resultException);
        }
        return (boolean) results.resultValue;
    }

    public void insertMercuryOrders(List<MercuryOrderDto> newOrders, String json) {
        for (MercuryOrderDto order : newOrders) {
            try {
                create(order, json);
            } catch (Exception e) {
                log.error("Unable to insert mercury order for participant " + order.getDdpParticipantId() + " with barcode "
                        + order.getBarcode(), e);
            }
        }
    }

}
