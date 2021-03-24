package org.broadinstitute.ddp.handlers;

import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.handlers.util.EmptyPayload;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.util.CountryCode;
import org.broadinstitute.ddp.util.CountryUtil;
import org.broadinstitute.ddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GetCountryCodeHandler extends AbstractRequestHandler<EmptyPayload>
{
    private static final Logger logger = LoggerFactory.getLogger(GetCountryCodeHandler.class);

    // static list is essentially a cache since countries are rarely added to the world
    private static List<CountryCode> countryCodeList = new ArrayList<>();

    public GetCountryCodeHandler(EDCClient edc, Config config) {
        super(EmptyPayload.class, edc, config);
    }

    public GetCountryCodeHandler(Config config) {
        super(EmptyPayload.class, config);
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        synchronized (countryCodeList) {
            if (countryCodeList.isEmpty()) {
                countryCodeList = new CountryUtil().getUSFirstNameOrderedCountries();
            }
        }
        return new Result(200, new GsonBuilder().serializeNulls().create().toJson(countryCodeList));
    }
}

