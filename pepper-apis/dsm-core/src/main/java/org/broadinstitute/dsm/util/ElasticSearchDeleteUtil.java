package org.broadinstitute.dsm.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.patch.DeleteType;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class ElasticSearchDeleteUtil {

    public static void deleteFromIndexById(String ddpParticipantId, int id, DDPInstanceDto ddpInstanceDto, DeleteType deleteType)
            throws Exception {
        if (DeleteType.ONC_HISTORY_DETAIL.equals(deleteType)) {
            deleteOncHistoryDetailById(ddpParticipantId, id, ddpInstanceDto);
        } else if (DeleteType.TISSUE.equals(deleteType)) {
            deleteTissueById(ddpParticipantId, id, ddpInstanceDto);
        } else if (DeleteType.SM_ID.equals(deleteType)) {
            deleteSmIdById(ddpParticipantId, id, ddpInstanceDto);
        }

    }

    private static void deleteSmIdById(String ddpParticipantId, int id, DDPInstanceDto ddpInstanceDto) throws Exception {
        deleteFromDSMObjectById(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId, id,
                ESObjectConstants.SMID_PK, ESObjectConstants.SMID);
    }

    private static void deleteTissueById(String ddpParticipantId, int tissueId, DDPInstanceDto ddpInstanceDto) throws Exception {
        deleteFromDSMObjectById(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId, tissueId,
                ESObjectConstants.TISSUE_ID, ESObjectConstants.TISSUE);
    }

    public static void deleteOncHistoryDetailById(String ddpParticipantId, int oncHistoryDetailId, DDPInstanceDto ddpInstanceDto)
            throws Exception {
        deleteFromDSMObjectById(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId, oncHistoryDetailId,
                ESObjectConstants.ONC_HISTORY_DETAIL_ID, ESObjectConstants.ONC_HISTORY_DETAIL);
    }

    private static void deleteFromDSMObjectById(String index, String ddpParticipantId, int id, String primaryKeyName, String objectName)
            throws Exception {
        Map<String, Object> esDsmMap = ElasticSearchUtil.getObjectsMap(index, ddpParticipantId, ESObjectConstants.DSM);
        List<Map<String, Object>> objects =
                (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get(ESObjectConstants.DSM)).get(objectName);
        objects.removeIf(stringObjectMap -> (int) stringObjectMap.get(primaryKeyName) == id);
        ((HashMap<String, List<Map<String, Object>>>) esDsmMap.get(ESObjectConstants.DSM)).put(objectName, objects);
        ElasticSearchUtil.updateRequest(ddpParticipantId, index, new HashMap<>(
                Map.of(ESObjectConstants.DSM, esDsmMap.get(ESObjectConstants.DSM))));
    }
}