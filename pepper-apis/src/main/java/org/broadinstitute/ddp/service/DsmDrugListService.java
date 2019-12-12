package org.broadinstitute.ddp.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.List;

import com.google.gson.Gson;

import com.google.gson.reflect.TypeToken;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.exception.DDPException;

import org.broadinstitute.ddp.util.Auth0Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DsmDrugListService {

    private final URL baseUrl;
    private static final Logger LOG = LoggerFactory.getLogger(DsmDrugListService.class);
    private static final String SIGNER = "org.broadinstitute.kdux";

    public DsmDrugListService(URL baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<String> fetchDrugs(String dsmJWTSecret) {

        List<String> dsmDrugs;

        String dsmToken;
        try {
            dsmToken = Auth0Util.generateShortLivedJwtToken(dsmJWTSecret, SIGNER);
        } catch (UnsupportedEncodingException e) {
            throw new DDPException("Failed to generate Dsm JWT Token ", e);
        }

        HttpUrl dsmUrl = HttpUrl.get(baseUrl).newBuilder()
                .addEncodedPathSegments(RouteConstants.API.DSM_DRUGS)
                .build();

        LOG.info("Connecting to DSM, url: " + dsmUrl.toString());

        Request request = new Request.Builder()
                .url(dsmUrl)
                .addHeader("Authorization", "Bearer " + dsmToken)
                .build();

        Response response = null;
        try {
            response = new OkHttpClient().newCall(request).execute();
            String responseBody = response.body().string();
            Type listType = new TypeToken<List<String>>(){}.getType();
            dsmDrugs = new Gson().fromJson(responseBody, listType);
        } catch (IOException e) {
            throw new DDPException("Failed fetching Dsm drugs", e);
        }

        return dsmDrugs;
    }

}
