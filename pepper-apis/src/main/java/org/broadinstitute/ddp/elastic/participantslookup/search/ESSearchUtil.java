package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.addWildcards;

import com.google.gson.Gson;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType;
import org.broadinstitute.ddp.util.GsonUtil;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;


/**
 * Class defining constants and util methods used during participants/users searching in ElasticSearch
 */
public abstract class ESSearchUtil {

    static final String SOURCE__PROFILE = "profile";
    static final String SOURCE__GOVERNED_USERS = "governedUsers";
    static final String SOURCE__STATUS = "status";
    static final String SOURCE__INVITATIONS = "invitations";
    static final String SOURCE__PROXIES = "proxies";

    // Field value (to fetch from source 'invitations')
    static final String GUID = "guid";


    /**
     * ElasticSearch "_source" names (to fetch data from index "users")
     */
    static final String[] USERS__INDEX__SOURCE = {
            SOURCE__PROFILE,
            SOURCE__GOVERNED_USERS
    };

    /**
     * ElasticSearch "_source" names (to fetch data from index "participants_structured")
     */
    static final String[] PARTICIPANTS_STRUCTURED__INDEX__SOURCE = {
            SOURCE__STATUS,
            SOURCE__PROFILE,
            SOURCE__INVITATIONS,
            SOURCE__PROXIES
    };

    static final Gson GSON = GsonUtil.standardGson();

    /**
     * Helper method for building {@link QueryBuilder} to search by specified 'query' in list of fields
     * specified in {@link ESParticipantsLookupField} - taken only fields of a specified 'indexType' (or all index types).
     * @param indexType type of index
     * @param query string fragment to do full-text search
     * @return QueryBuilder - created query
     */
    public static QueryBuilder queryLookupFieldsOfIndex(ESParticipantsLookupIndexType indexType, String query) {
        var queryBuilder = QueryBuilders.queryStringQuery(addWildcards(query)).defaultOperator(Operator.OR);
        for (var field : ESParticipantsLookupField.values()) {
            if (field.isQueryFieldForIndex(indexType)) {
                queryBuilder.field(field.getEsField());
            }
        }
        return queryBuilder;
    }
}
