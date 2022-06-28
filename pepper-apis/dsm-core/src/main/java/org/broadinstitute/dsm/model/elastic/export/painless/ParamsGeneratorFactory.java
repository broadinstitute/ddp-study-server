package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.List;

import org.broadinstitute.dsm.model.elastic.export.generate.Generator;

public class ParamsGeneratorFactory {
    private Object source;
    private String realm;

    public ParamsGeneratorFactory(Object source, String realm) {
        this.source = source;
        this.realm = realm;
    }

    public Generator instance() {
        Generator generator;
        if (source instanceof List) {
            generator = new CollectionParamsGenerator<>((List)source, realm);
        } else {
            generator = new ParamsGenerator(source, realm);
        }
        return generator;
    }
}
