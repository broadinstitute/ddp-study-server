package org.broadinstitute.dsm.model.elastic.search;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UnparsedESParticipantDto extends ElasticSearchParticipantDto {
    @Getter
    private final Map<String, Object> dataAsMap;

    public UnparsedESParticipantDto(Map<String, Object> dataAsMap) {
        super();
        this.dataAsMap = dataAsMap;
    }

    @Override
    public void setDdp(String ddp) {
        if (dataAsMap != null) {
            dataAsMap.put("ddp", ddp);
        }
    }

    public List<String> getProxies() {
        Object proxyIds = getDataAsMap().get("proxies");
        if (proxyIds instanceof List) {
            return (List<String>) proxyIds;
        }
        return Collections.emptyList();
    }

}
