package org.broadinstitute.lddp.datstat;

import com.google.api.client.util.Key;
import com.google.gson.annotations.SerializedName;
import spark.utils.StringUtils;

import java.util.Collection;

/**
 * Wrapper around DatStat json
 */
public class SurveySession {

    @SerializedName("Uri")
    @Key("Uri")
    private String uri;

    @Key("Data")
    private Collection<SurveyData> data;

    public SurveySession(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    public String getSessionId() {
        String sessionId = "";
        if (StringUtils.isNotEmpty(getUri())) {
            String[] splitResult = getUri().split(SurveyService.SURVEY_SESSIONS_PATH);
            if (splitResult != null && splitResult.length == 2) {
                sessionId = splitResult[1].replace("/","");
            }
        }
        return sessionId;
    }

    private class SurveyData {

        @Key("DATSTAT.SESSIONID")
        private String sessionId;
    }

}