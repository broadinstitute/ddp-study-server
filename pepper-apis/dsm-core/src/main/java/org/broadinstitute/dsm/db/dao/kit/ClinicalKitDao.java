package org.broadinstitute.dsm.db.dao.kit;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dto.kit.ClinicalKitDto;
import org.broadinstitute.dsm.model.gp.ClinicalKitWrapper;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class ClinicalKitDao {
    private static final Logger logger = LoggerFactory.getLogger(ClinicalKitDao.class);
    private final static String SQL_GET_CLINICAL_KIT_BASED_ON_SM_ID_VALUE = "SELECT p.ddp_participant_id, accession_number, ddp.instance_name, bsp_organism, bsp_collection, "
            + "kit_type_name, bsp_material_type, bsp_receptacle_type, ddp.ddp_instance_id "
            + "FROM sm_id sm "
            + "LEFT JOIN ddp_tissue t on (t.tissue_id  = sm.tissue_id) "
            + "LEFT JOIN ddp_onc_history_detail oD on (oD.onc_history_detail_id = t.onc_history_detail_id) "
            + "LEFT JOIN ddp_medical_record mr on (mr.medical_record_id = oD.medical_record_id) "
            + "LEFT JOIN ddp_institution inst on  (mr.institution_id = inst.institution_id AND NOT mr.deleted <=> 1) "
            + "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) "
            + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
            + "LEFT JOIN sm_id_type sit on (sit.sm_id_type_id = sm.sm_id_type_id) "
            + "LEFT JOIN kit_type ktype on ( sit.kit_type_id = ktype.kit_type_id) "
            + "WHERE sm.sm_id_value = ? ";

    public Optional<ClinicalKitWrapper> getClinicalKitFromSMId(String smIdValue) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_CLINICAL_KIT_BASED_ON_SM_ID_VALUE)) {
                stmt.setString(1, smIdValue);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ClinicalKitDto clinicalKitDto = new ClinicalKitDto(
                                null,
                                rs.getString(DBConstants.BSP_COLLECTION),
                                rs.getString(DBConstants.BSP_ORGANISM),
                                rs.getString(DBConstants.BSP_MATERIAL_TYPE),
                                rs.getString(DBConstants.BSP_RECEPTABLE_TYPE),
                                null,
                                null,
                                null,
                                null,
                                null,
                                rs.getString(DBConstants.ACCESSION_NUMBER),
                                null
                        );
                        clinicalKitDto.setSampleType(rs.getString(DBConstants.KIT_TYPE_NAME));
                        ClinicalKitWrapper clinicalKitWrapper = new ClinicalKitWrapper(clinicalKitDto,
                                Integer.parseInt(rs.getString(DBConstants.DDP_INSTANCE_ID)),
                                rs.getString(DBConstants.DDP_PARTICIPANT_ID));
                        dbVals.resultValue = clinicalKitWrapper;
                        logger.info("found clinical kit for sm id value: " + smIdValue);
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

    public ClinicalKitDto getClinicalKitBasedOnSmId(String smIdValue) {
        logger.info("Checking the kit for SM Id value " + smIdValue);
        Optional<ClinicalKitWrapper> maybeClinicalKitWrapper = getClinicalKitFromSMId(smIdValue);
        maybeClinicalKitWrapper.orElseThrow();
        ClinicalKitWrapper clinicalKitWrapper = maybeClinicalKitWrapper.get();
        ClinicalKitDto clinicalKitDto = clinicalKitWrapper.getClinicalKitDto();
        DDPInstance ddpInstance = DDPInstance.getDDPInstanceById(clinicalKitWrapper.getDdpInstanceId());
        clinicalKitDto.setNecessaryParticipantDataToClinicalKit(clinicalKitWrapper.getDdpParticipantId(), ddpInstance);
        if (StringUtils.isNotBlank(clinicalKitDto.getAccessionNumber())) {
            return clinicalKitDto;
        }
        throw new RuntimeException("The kit doesn't have an accession number! SM ID is: " + smIdValue);
    }
}
