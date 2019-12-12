package org.broadinstitute.ddp.transformers;

import com.google.gson.Gson;
import org.broadinstitute.ddp.util.GsonUtil;
import spark.ResponseTransformer;

public class SimpleJsonTransformer implements ResponseTransformer {

    private final Gson gson;

    public SimpleJsonTransformer() {
        this(GsonUtil.standardGson());
    }

    public SimpleJsonTransformer(Gson gson) {
        this.gson = gson;
    }

    @Override
    public String render(Object model) {
        return gson.toJson(model);
    }
}
