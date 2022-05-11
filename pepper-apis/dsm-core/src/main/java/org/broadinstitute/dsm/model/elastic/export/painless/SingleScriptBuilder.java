package org.broadinstitute.dsm.model.elastic.export.painless;

public class SingleScriptBuilder extends BaseScriptBuilder {

    private static final String SCRIPT =
            "if (ctx._source.dsm.#propertyName == null) {ctx._source.dsm.#propertyName = params.dsm.#propertyName} "
                    + "else {for (entry in params.dsm.#propertyName.entrySet()) { "
                    + "ctx._source.dsm.#propertyName.put(entry.getKey(), entry.getValue()) }}";

    public SingleScriptBuilder(String propertyName) {
        super(propertyName);
    }

    @Override
    public String build() {
        return SCRIPT.replace("#propertyName", propertyName);
    }
}
