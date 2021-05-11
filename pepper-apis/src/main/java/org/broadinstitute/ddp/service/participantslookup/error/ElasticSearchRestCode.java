package org.broadinstitute.ddp.service.participantslookup.error;

public class ElasticSearchRestCode {

    public static final String ELASTIC_SEARCH__ERROR_CODE__PREFIX = "ElasticSearch__";

    public static String getResponseBodyCodeForElasticSearchError(String code) {
        return ELASTIC_SEARCH__ERROR_CODE__PREFIX + code;
    }
}
