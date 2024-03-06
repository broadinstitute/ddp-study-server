package org.broadinstitute.dsm.service.participantdata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;

@Slf4j
public class ParticipantDataService {
    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();

    public static List<DuplicateParticipantData> handleDuplicateRecords(List<ParticipantData> ptpDataList) {
        TreeMap<Integer, ParticipantData> idToRecord = ptpDataList.stream().collect(
                Collectors.toMap(ParticipantData::getParticipantDataId, pd -> pd, (id1, id2) -> id1, TreeMap::new));

        ParticipantData firstEntry = idToRecord.firstEntry().getValue();
        int firstEntryId = idToRecord.firstKey();
        idToRecord.remove(idToRecord.firstKey());

        Map<String, String> firstDataMap = firstEntry.getDataMap();
        Set<String> firstDataMapKeys = firstDataMap.keySet();

        String fieldTypeId = ptpDataList.get(0).getRequiredFieldTypeId();
        List<DuplicateParticipantData> duplicateDataList = new ArrayList<>();
        idToRecord.forEach((id, pd) -> {
            log.info("Comparing duplicate ParticipantData record {} to base record {} for {}", id, firstEntryId,
                    fieldTypeId);
            DuplicateParticipantData duplicateData = new DuplicateParticipantData(fieldTypeId, firstEntryId, id);
            duplicateDataList.add(duplicateData);

            Map<String, String> dataMap = pd.getDataMap();
            Set<String> overlappingKeys = new HashSet<>(CollectionUtils.retainAll(firstDataMapKeys, dataMap.keySet()));
            duplicateData.setCommonKeys(overlappingKeys);
            if (dataMap.keySet().size() != firstDataMapKeys.size()
                    || overlappingKeys.size() != firstDataMapKeys.size()) {
                duplicateData.setBaseOnlyKeys(
                        new HashSet<>(CollectionUtils.subtract(firstDataMapKeys, dataMap.keySet())));
                duplicateData.setDuplicateOnlyKeys(
                        new HashSet<>(CollectionUtils.subtract(dataMap.keySet(), firstDataMapKeys)));
            }

            // for overlapping keys, produce a list of the values that differ
            overlappingKeys.forEach(key -> {
                if (!firstDataMap.get(key).equals(dataMap.get(key))) {
                    duplicateData.setDifferentValues(key, firstDataMap.get(key), dataMap.get(key));
                }
            });
        });
        return duplicateDataList;
    }

    /**
     * Update ParticipantData entities in ElasticSearch based on participantData in the database
     */
    public static void updateEsParticipantData(String ddpParticipantId, DDPInstance instance) {
        ElasticSearchService.updateEsParticipantData(ddpParticipantId,
                participantDataDao.getParticipantData(ddpParticipantId), instance);
    }
}
