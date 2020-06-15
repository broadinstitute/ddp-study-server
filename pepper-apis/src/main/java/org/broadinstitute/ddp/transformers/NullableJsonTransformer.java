package org.broadinstitute.ddp.transformers;

import com.google.gson.Gson;
import org.broadinstitute.ddp.util.GsonUtil;
import spark.ResponseTransformer;

public class NullableJsonTransformer implements ResponseTransformer {

    private final Gson gson;

    public NullableJsonTransformer() {
        this(GsonUtil.standardGson());
    }

    public NullableJsonTransformer(Gson gson) {
        this.gson = gson;
    }

    @Override
    public String render(Object model) {
        // If the response from a route is `null`, then return no content instead of "null".
        return model == null ? "" : gson.toJson(model);
    }
}
