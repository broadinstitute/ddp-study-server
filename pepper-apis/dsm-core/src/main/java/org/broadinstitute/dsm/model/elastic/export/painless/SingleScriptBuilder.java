package org.broadinstitute.dsm.model.elastic.export.painless;

public class SingleScriptBuilder implements ScriptBuilder {

    private final static String SCRIPT = "if (ctx._source.dsm.#propertyName == null) " +
            "{ctx._source.dsm.#propertyName = params.dsm.#propertyName} " +
            "else {for (entry in params.dsm.#propertyName.entrySet()) { ctx._source.dsm.#propertyName.put(entry.getKey(), entry.getValue()) }}";

    private final String propertyName;

    public SingleScriptBuilder(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public String build() {
        return SCRIPT.replace("#propertyName", propertyName);
    }
}
