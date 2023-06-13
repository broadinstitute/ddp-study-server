package org.broadinstitute.dsm.db.dao.ddp.onchistory;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.util.DaoUtil;
import org.broadinstitute.dsm.db.dao.util.ResultsBuilder;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.lddp.db.SimpleResult;

public class OncHistoryDetailDaoImpl implements OncHistoryDetailDao<OncHistoryDetailDto> {

    public static final String SQL_SELECT_TISSUE_RECEIVED =
            "SELECT tissue_received FROM ddp_onc_history_detail WHERE onc_history_detail_id = ?";
    public static final String SQL_SELECT_TISSUES_FOR_ONC_HISTORY = "SELECT * from ddp_tissue where onc_history_detail_id = ?";

    public static final String SQL_SELECT_BY_ID = "SELECT * FROM ddp_onc_history_detail WHERE onc_history_detail_id = ?;";

    private static final String SQL_DELETE_BY_ID = "DELETE FROM ddp_onc_history_detail WHERE onc_history_detail_id = ?";

    @Override
    public int create(OncHistoryDetailDto oncHistoryDetailDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = DaoUtil.deleteById(id, SQL_DELETE_BY_ID);
        if (simpleResult.resultException != null) {
            throw new DsmInternalError("Error deleting ddp_onc_history_detail with id: " + id, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<OncHistoryDetailDto> get(long id) {
        OncHistoryDetailDaoImpl.BuildOncHistoryDetailDto builder = new OncHistoryDetailDaoImpl.BuildOncHistoryDetailDto();
        SimpleResult res = DaoUtil.getById(id, SQL_SELECT_BY_ID, builder);
        if (res.resultException != null) {
            throw new RuntimeException("Error getting onc history detail with id: " + id,
                    res.resultException);
        }
        return (Optional<OncHistoryDetailDto>) res.resultValue;
    }

    private static class BuildOncHistoryDetailDto implements ResultsBuilder {
        public Object build(ResultSet rs) throws SQLException {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            Map<String, Object> row = new HashMap<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.put(md.getColumnName(i), rs.getObject(i));
            }
            return new OncHistoryDetailDto(row);
        }
    }

    @Override
    public boolean hasReceivedDate(int oncHistoryDetailId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_TISSUE_RECEIVED)) {
                stmt.setInt(1, oncHistoryDetailId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String receivedDate = rs.getString(DBConstants.TISSUE_RECEIVED);
                        if (StringUtils.isNotBlank(receivedDate)) {
                            dbVals.resultValue = true;
                        } else {
                            dbVals.resultValue = false;
                        }
                    } else {
                        dbVals.resultException = new RuntimeException(" The onc history detail id was not found in the table!");
                    }
                } catch (SQLException ex) {
                    dbVals.resultException = ex;
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException(" Error getting the received date of the OncHistoryDetail with Id:" + oncHistoryDetailId,
                    results.resultException);
        }
        return (Boolean) results.resultValue;
    }

    public OncHistoryDetail getRandomOncHistoryDetail(@NonNull String oncHistoryDetailId, String realm) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            OncHistoryDetail oncHistoryDetail = null;
            try (PreparedStatement stmt = conn.prepareStatement(
                    OncHistoryDetail.SQL_SELECT_ONC_HISTORY_DETAIL + QueryExtension.BY_ONC_HISTORY_DETAIL_ID)) {
                stmt.setString(1, realm);
                stmt.setString(2, oncHistoryDetailId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        oncHistoryDetail = new OncHistoryDetail(rs.getLong(DBConstants.ONC_HISTORY_DETAIL_ID),
                                rs.getLong(DBConstants.MEDICAL_RECORD_ID), rs.getString(DBConstants.DATE_PX),
                                rs.getString(DBConstants.TYPE_PX), rs.getString(DBConstants.LOCATION_PX),
                                rs.getString(DBConstants.HISTOLOGY), rs.getString(DBConstants.ACCESSION_NUMBER),
                                rs.getString(DBConstants.FACILITY),
                                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.PHONE),
                                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX),
                                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.NOTES),
                                rs.getString(DBConstants.REQUEST),
                                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT),
                                rs.getString(
                                        DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT_BY),
                                rs.getString(
                                        DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_CONFIRMED),
                                rs.getString(
                                        DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT_2),
                                rs.getString(
                                        DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT_2_BY),
                                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER
                                        + DBConstants.FAX_CONFIRMED_2), rs.getString(
                                DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT_3),
                                rs.getString(
                                        DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT_3_BY),
                                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER
                                        + DBConstants.FAX_CONFIRMED_3), rs.getString(DBConstants.TISSUE_RECEIVED),
                                rs.getString(DBConstants.GENDER), rs.getString(
                                DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER
                                        + DBConstants.ADDITIONAL_VALUES_JSON), null, rs.getString(DBConstants.TISSUE_PROBLEM_OPTION),
                                rs.getString(DBConstants.DESTRUCTION_POLICY), rs.getBoolean(DBConstants.UNABLE_OBTAIN_TISSUE),
                                rs.getString(DBConstants.PARTICIPANT_ID), rs.getString(DBConstants.DDP_PARTICIPANT_ID),
                                rs.getLong(DBConstants.DDP_INSTANCE_ID));
                    }

                }
                if (oncHistoryDetail != null) {
                    ArrayList<Tissue> tissues = new ArrayList<>();
                    try (PreparedStatement statementTissue = conn.prepareStatement(SQL_SELECT_TISSUES_FOR_ONC_HISTORY)) {
                        statementTissue.setString(1, oncHistoryDetailId);
                        try (ResultSet rsTissue = stmt.executeQuery()) {
                            while (rsTissue.next()) {
                                tissues.add(Tissue.getTissue(rsTissue));
                            }
                            oncHistoryDetail.setTissues(tissues);
                        }
                    }
                }
                dbVals.resultValue = oncHistoryDetail;

            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting oncHistoryDetails w/ id " + oncHistoryDetailId, results.resultException);
        }

        return (OncHistoryDetail) results.resultValue;
    }
}
