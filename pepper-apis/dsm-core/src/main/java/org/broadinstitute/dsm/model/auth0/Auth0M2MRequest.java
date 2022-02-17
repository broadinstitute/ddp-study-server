package org.broadinstitute.dsm.model.auth0;

import lombok.Data;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Data
public class Auth0M2MRequest {
    public List<NameValuePair> params;

    public Auth0M2MRequest(String clientId, String grantType, String clientSecret, String audience, Map<String, String> claims, String audienceNameSpace){

        claims.put("client_id", clientId);
        claims.put("grant_type", grantType);
        claims.put("client_secret", clientSecret);
        claims.put("audience", audience);

        //adding the request values with key name (audienceNameSpace + key) because auth0 requires claims to have a valid url as the name
        this.params = new ArrayList<>();
        for (String key: claims.keySet()){
            params.add(  new BasicNameValuePair(audienceNameSpace+key, claims.get(key)));
        }
    }

}
