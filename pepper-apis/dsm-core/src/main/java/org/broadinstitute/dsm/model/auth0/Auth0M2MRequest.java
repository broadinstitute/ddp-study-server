package org.broadinstitute.dsm.model.auth0;

import lombok.Data;
import lombok.NonNull;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Data
public class Auth0M2MRequest {
    public List<NameValuePair> params;
    private final String clientIdKey = "client_id";
    private final String grantTypeKey = "grant_type";
    private final String clientSecretKey = "client_secret";
    private final String audienceKey = "audience";

    public Auth0M2MRequest(@NonNull String clientId,@NonNull String grantType,@NonNull String clientSecret,@NonNull String audience, Map<String, String> claims, String audienceNameSpace){

        //adding the request values with key name (audienceNameSpace + key) because auth0 requires claims to have a valid url as the name
        this.params = new ArrayList<>();
        for (String key: claims.keySet()){
            String k = key;
            if(key.indexOf(audienceNameSpace) == -1)
                k = audienceNameSpace + k;
            params.add(  new BasicNameValuePair(k, claims.get(key)));
        }
        params.add(  new BasicNameValuePair(clientIdKey, clientId));
        params.add(  new BasicNameValuePair(grantTypeKey, grantType));
        params.add(  new BasicNameValuePair(clientSecretKey, clientSecret));
        params.add(  new BasicNameValuePair(audienceKey, audience));

    }

}
