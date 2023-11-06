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
        Map<String, Object> esDsmMap = ElasticSearchUtil.getObjectsMap(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId,
                ESObjectConstants.DSM);
        List<Map<String, Object>> smIds =
                (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get(ESObjectConstants.DSM)).get(ESObjectConstants.SMID);
        smIds.removeIf(stringObjectMap -> (int) stringObjectMap.get("smIdPk") == id);
        esDsmMap.put(ESObjectConstants.SMID, smIds);

        ElasticSearchUtil.updateRequest(ddpParticipantId, ddpInstanceDto.getEsParticipantIndex(), new HashMap<>(
                Map.of(ESObjectConstants.DSM, esDsmMap)));
    }

    private static void deleteTissueById(String ddpParticipantId, int tissueId, DDPInstanceDto ddpInstanceDto) throws Exception {
        Map<String, Object> esDsmMap = ElasticSearchUtil.getObjectsMap(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId,
                ESObjectConstants.DSM);
        List<Map<String, Object>> tissues =
                (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get(ESObjectConstants.DSM)).get(ESObjectConstants.TISSUE);
        tissues.removeIf(stringObjectMap -> (int) stringObjectMap.get("tissueId") == tissueId);
        ((HashMap<String,List<Map<String, Object>>>) esDsmMap.get(ESObjectConstants.DSM)).put(ESObjectConstants.TISSUE, tissues);

        ElasticSearchUtil.updateRequest(ddpParticipantId, ddpInstanceDto.getEsParticipantIndex(), new HashMap<>(
                Map.of(ESObjectConstants.DSM, esDsmMap.get(ESObjectConstants.DSM))));

    }

    public static void deleteOncHistoryDetailById(String ddpParticipantId, int oncHistoryDetailId, DDPInstanceDto ddpInstanceDto)
            throws Exception {

        Map<String, Object> esDsmMap = ElasticSearchUtil.getObjectsMap(ddpInstanceDto.getEsParticipantIndex(), ddpParticipantId,
                ESObjectConstants.DSM);
        List<Map<String, Object>> oncHistoryDetails =
                (List<Map<String, Object>>) ((Map<String, Object>) esDsmMap.get(ESObjectConstants.DSM)).get(
                        ESObjectConstants.ONC_HISTORY_DETAIL);
        oncHistoryDetails.removeIf(stringObjectMap -> (int) stringObjectMap.get("oncHistoryDetailId") == oncHistoryDetailId);
        ((HashMap<String,List<Map<String, Object>>>) esDsmMap.get(ESObjectConstants.DSM)).put(ESObjectConstants.ONC_HISTORY_DETAIL,
                oncHistoryDetails);
        ElasticSearchUtil.updateRequest(ddpParticipantId, ddpInstanceDto.getEsParticipantIndex(), new HashMap<>(
                Map.of(ESObjectConstants.DSM, esDsmMap.get(ESObjectConstants.DSM))));

    }
}
