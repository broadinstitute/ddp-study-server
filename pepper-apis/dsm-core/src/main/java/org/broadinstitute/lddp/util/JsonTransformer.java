package org.broadinstitute.lddp.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import spark.ResponseTransformer;

public class JsonTransformer implements ResponseTransformer {

    private Gson gson = new GsonBuilder().serializeNulls().create();

    @Override
    public String render(Object model) {
        return gson.toJson(model);
    }

}
