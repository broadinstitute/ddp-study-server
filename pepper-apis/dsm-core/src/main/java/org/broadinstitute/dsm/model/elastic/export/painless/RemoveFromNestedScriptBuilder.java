package org.broadinstitute.dsm.model.elastic.export.painless;

public class RemoveFromNestedScriptBuilder extends NestedScriptBuilder {

    private static final String SCRIPT = "if (ctx._source.dsm.#propertyName != null) "
            + "{ ctx._source.dsm.#propertyName.removeIf(tag -> tag.#uniqueIdentifier == params.dsm.#propertyName.#uniqueIdentifier); }";

    public RemoveFromNestedScriptBuilder(String propertyName, String uniqueIdentifier) {
        super(propertyName, uniqueIdentifier, SCRIPT);
    }

    public RemoveFromNestedScriptBuilder() {
        super(SCRIPT);
    }
}
