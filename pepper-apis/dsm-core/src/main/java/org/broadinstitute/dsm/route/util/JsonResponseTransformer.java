package org.broadinstitute.dsm.route.util;

import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import spark.ResponseTransformer;

public class JsonResponseTransformer implements ResponseTransformer {

    @Override
    public String render(Object model) {
        return ObjectMapperSingleton.writeValueAsString(model);
    }
}
