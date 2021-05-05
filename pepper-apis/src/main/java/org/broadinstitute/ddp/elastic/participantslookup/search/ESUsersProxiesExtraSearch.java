package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.PROFILE__GUID;

import java.util.Map;

import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;


/**
 * Search step 3.
 * In index "users" search proxies by set of proxy GUIDs (that proxy-users which not found during search step 1
 * but was detected during participants (governed users) search (step2).
 */
public class ESUsersProxiesExtraSearch extends ESUsersProxiesSearch {

    public ESUsersProxiesExtraSearch(RestHighLevelClient esClient, Map<String, String> governedUserToProxy) {
        super(esClient, governedUserToProxy);
    }

    @Override
    protected AbstractQueryBuilder createQuery() {
        // find all governedUsers for which found proxies in 'users'
        BoolQueryBuilder proxyExtraQueryBuilder = new BoolQueryBuilder();
        for (var proxy : governedUserToProxy.values()) {
            proxyExtraQueryBuilder.should(QueryBuilders.matchQuery(PROFILE__GUID.getEsField(), proxy));
        }
        return proxyExtraQueryBuilder;
    }
}
