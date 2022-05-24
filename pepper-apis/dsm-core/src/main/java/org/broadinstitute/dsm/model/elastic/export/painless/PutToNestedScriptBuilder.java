package org.broadinstitute.dsm.model.elastic.export.painless;

public class PutToNestedScriptBuilder extends NestedScriptBuilder {

    private static final String SCRIPT =
            "if (ctx._source.dsm.#propertyName == null) {ctx._source.dsm.#propertyName = [params.dsm.#propertyName]} "
                    + "else {def targets = ctx._source.dsm.#propertyName.findAll(obj -> obj.containsKey('#uniqueIdentifier') "
                    + "&& obj.#uniqueIdentifier == params.dsm.#propertyName.#uniqueIdentifier); "
                    + "if (targets.size() == 0) { ctx._source.dsm.#propertyName.add(params.dsm.#propertyName) } "
                    + "else { for(target in targets) { for (entry in params.dsm.#propertyName.entrySet()) { "
                    + "target.put(entry.getKey(), entry.getValue()) } }}}";

    public PutToNestedScriptBuilder(String propertyName, String uniqueIdentifier) {
        super(propertyName, uniqueIdentifier, SCRIPT);
    }

    public PutToNestedScriptBuilder() {
        super(SCRIPT);
    }
}
