package org.broadinstitute.dsm.model;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class TissueListWrapper {
    private Map<String, Object> data;
    private TissueList tissueList;
    private static final Logger logger = LoggerFactory.getLogger(TissueListWrapper.class);

    public TissueListWrapper(Map<String, Object> data, TissueList tissueList) {
        this.data = data;
        this.tissueList = tissueList;
    }

    public static List<TissueListWrapper> getTissueListData(DDPInstance instance, Map<String, String> filters, List<TissueList> tissueLists) {
        Map<String, Map<String, Object>> participantESData = null;
        boolean hasESData = instance.getParticipantIndexES() != null;
        if (filters != null && filters.containsKey("ES") && hasESData) {
            participantESData = ElasticSearchUtil.getFilteredDDPParticipantsFromES(instance, filters.get("ES"));
        }
        else if ((filters == null || !filters.containsKey("ES")) || (filters.containsKey("ES") && participantESData == null) || !hasESData) {
            participantESData = ElasticSearchUtil.getESData(instance);
        }
        List<TissueListWrapper> results = new ArrayList<>();
        long timeBegin = System.currentTimeMillis();
        for (int i = 0; i < tissueLists.size(); i++) {
            TissueList tissueList = tissueLists.get(i);
            String ddpParticipantId = tissueList.getDdpParticipantId();
            if (StringUtils.isNotBlank(ddpParticipantId) && participantESData != null) {
                Map<String, Object> esData = participantESData.get(ddpParticipantId);
                if (esData != null) {
                    results.add(new TissueListWrapper(esData, tissueList));
                }
                //                else {
                //                    throw new RuntimeException("ES DATA not found for DDPParticipantId " + ddpParticipantId);
                //                }
            }
        }
        long timeEnd = System.currentTimeMillis();
        logger.info("Time it took for " + results.size() + " results to match: " + (timeEnd - timeBegin));
        return results;
    }
}
