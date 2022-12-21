package org.broadinstitute.dsm.db.dao.kit;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dto.kit.ClinicalKitDto;
import org.broadinstitute.dsm.model.gp.ClinicalKitWrapper;
import org.broadinstitute.dsm.route.ClinicalKitsRoute;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class ClinicalKitDao {

    public static final String PECGS = "PE-CGS";
    private static final String SQL_GET_CLINICAL_KIT_BASED_ON_SM_ID_VALUE =
            "SELECT p.ddp_participant_id, accession_number, ddp.instance_name, t.collaborator_sample_id, date_px,  "
                    + "kit_type_name, bsp_material_type, bsp_receptacle_type, ddp.ddp_instance_id FROM sm_id sm "
                    + "LEFT JOIN ddp_tissue t on (t.tissue_id  = sm.tissue_id) "
                    + "LEFT JOIN ddp_onc_history_detail oD on (oD.onc_history_detail_id = t.onc_history_detail_id) "
                    + "LEFT JOIN ddp_medical_record mr on (mr.medical_record_id = oD.medical_record_id) "
                    + "LEFT JOIN ddp_institution inst on  (mr.institution_id = inst.institution_id AND NOT mr.deleted <=> 1) "
                    + "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) "
                    + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
                    + "LEFT JOIN sm_id_type sit on (sit.sm_id_type_id = sm.sm_id_type_id) "
                    + "LEFT JOIN kit_type ktype on ( sit.kit_type_id = ktype.kit_type_id) "
                    + "WHERE sm.sm_id_value = ? AND NOT sm.deleted <=> 1 ";
    private static final String SQL_SET_ACCESSION_TIME =
            "UPDATE sm_id SET received_date = ?, received_by = ? WHERE sm_id_value = ? AND NOT deleted <=> 1";

    public static Optional<ClinicalKitWrapper> getClinicalKitFromSMId(String smIdValue) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_CLINICAL_KIT_BASED_ON_SM_ID_VALUE)) {
                stmt.setString(1, smIdValue);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ClinicalKitDto clinicalKitDto = new ClinicalKitDto(null,
                                rs.getString(DBConstants.DDP_TISSUE_ALIAS + "." + DBConstants.COLLABORATOR_SAMPLE_ID), PECGS,
                                rs.getString(DBConstants.BSP_MATERIAL_TYPE), rs.getString(DBConstants.BSP_RECEPTABLE_TYPE), null, null,
                                null, null, null, rs.getString(DBConstants.ACCESSION_NUMBER), null);
                        clinicalKitDto.setSampleType(rs.getString(DBConstants.KIT_TYPE_NAME));
                        clinicalKitDto.setCollectionDate(rs.getString(DBConstants.DATE_PX));
                        ClinicalKitWrapper clinicalKitWrapper =
                                new ClinicalKitWrapper(clinicalKitDto, Integer.parseInt(rs.getString(DBConstants.DDP_INSTANCE_ID)),
                                        rs.getString(DBConstants.DDP_PARTICIPANT_ID));
                        dbVals.resultValue = clinicalKitWrapper;
                        log.info("found clinical kit for sm id value: " + smIdValue);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error getting clinical kit", e);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting clinicalKit based on smId " + smIdValue, results.resultException);
        }
        return Optional.ofNullable((ClinicalKitWrapper) results.resultValue);
    }

    public static void setAccessionTimeForSMID(String smIdValue) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SET_ACCESSION_TIME)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.setString(2, ClinicalKitsRoute.MERCURY);
                stmt.setString(3, smIdValue);
                int r = stmt.executeUpdate();
                if (r != 1) { //number of sm ids with that value
                    throw new RuntimeException("Update query for smId accession time updated " + r + " rows! with smId value " + smIdValue);
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error updating accession time for smId " + smIdValue, results.resultException);
        }
    }
}
