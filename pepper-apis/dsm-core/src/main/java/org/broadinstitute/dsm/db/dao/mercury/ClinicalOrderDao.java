package org.broadinstitute.dsm.db.dao.mercury;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.mercury.ClinicalOrderDto;
import org.broadinstitute.dsm.db.dto.mercury.ClinicalOrderUseCase;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class ClinicalOrderDao implements Dao<ClinicalOrderDto> {

    public static String SQL_GET_ALL_ORDERS_FOR_REALM = "select ms.order_id, ms.ddp_participant_id, "
            + "IFNULL(t.collaborator_sample_id, kit.bsp_collaborator_sample_id) as collaborator_sample_id, order_date, order_status, "
            + "status_date, status_detail,  IF(ms.tissue_id is null, \"Normal\", \"Tumor\") as sample_type "
            + "FROM mercury_sequencing ms "
            + "LEFT join ddp_tissue t on (ms.tissue_id = t.tissue_id  and ms.tissue_id ) "
            + "LEFT join ddp_kit_request kit on (ms.dsm_kit_request_id = kit.dsm_kit_request_id)"
            + "WHERE ms.ddp_instance_id = ? order by order_date desc";

    public static String COMPLETED_ORDER_STATUS = "Completed";

    @Override
    public int create(ClinicalOrderDto clinicalOrderDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<ClinicalOrderDto> get(long id) {
        return Optional.empty();
    }

    public Collection<ClinicalOrderDto> getOrdersForRealm(String realm, String projectId, String topicId,
                                                          ClinicalOrderUseCase clinicalOrderUseCase) {
        DDPInstanceDto ddpInstance = new DDPInstanceDao().getDDPInstanceByInstanceName(realm).orElseThrow();
        HashMap<String, ClinicalOrderDto> map = new HashMap<>();
        ArrayList<ClinicalOrderDto> noStatusOrders = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_ALL_ORDERS_FOR_REALM)) {
                stmt.setInt(1, ddpInstance.getDdpInstanceId());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString(DBConstants.COLLABORATOR_SAMPLE_ID) + "_" + rs.getString(DBConstants.MERCURY_ORDER_ID);
                        if (!map.containsKey(key)) {
                            String shortId = createShortIdFromCollaboratorSampleId(rs.getString(DBConstants.COLLABORATOR_SAMPLE_ID));
                            ClinicalOrderDto clinicalOrderDto = new ClinicalOrderDto(shortId,
                                    rs.getString(DBConstants.COLLABORATOR_SAMPLE_ID), rs.getString(DBConstants.MERCURY_ORDER_ID),
                                    rs.getString(DBConstants.MERCURY_ORDER_STATUS), rs.getLong(DBConstants.MERCURY_ORDER_DATE),
                                    rs.getLong(DBConstants.MERCURY_STATUS_DATE), rs.getString(DBConstants.MERCURY_STATUS_DETAIL),
                                    rs.getString(DBConstants.MERCURY_SAMPLE_TYPE)
                            );
                            map.put(key, clinicalOrderDto);
                            if (!COMPLETED_ORDER_STATUS.equals(clinicalOrderDto.getStatusDetail())) {
                                noStatusOrders.add(clinicalOrderDto);
                            }
                        }
                    }
                } catch (Exception e) {
                    dbVals.resultException = e;
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting mercury orders for realm " + realm,
                    results.resultException);
        }
        ArrayList<ClinicalOrderDto> array = new ArrayList(map.values());
        log.info(String.format("Found %d orders for realm %s", array.size(), realm));
        Collections.sort(array, new Comparator<ClinicalOrderDto>() {
            @Override
            public int compare(ClinicalOrderDto c1, ClinicalOrderDto c2) {
                return (int) (c2.orderDate - c1.orderDate);
            }
        });
        clinicalOrderUseCase.publishStatusActionMessage(array, projectId, topicId);
        return array;
    }

    private String createShortIdFromCollaboratorSampleId(String collabSampleId) {
        int index1 = collabSampleId.indexOf('_');
        int index2 = collabSampleId.indexOf('_', index1 + 1);
        if (index1 == -1 || index2 == -1 || index1 == index2) {
            log.error("Wrongly formatted collaborator sample id " + collabSampleId);
            return "";
        }
        return collabSampleId.substring(index1 + 1, index2);
    }
}
