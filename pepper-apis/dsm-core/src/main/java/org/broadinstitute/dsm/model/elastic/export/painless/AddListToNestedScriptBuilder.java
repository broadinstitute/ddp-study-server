package org.broadinstitute.dsm.model.elastic.export.painless;

import org.apache.commons.lang3.StringUtils;

public class AddListToNestedScriptBuilder extends NestedScriptBuilder {

    private static final String SCRIPT = "if (ctx._source.dsm.#propertyName == null) {ctx._source.dsm.#propertyName = params.dsm.#propertyName} "
            + "else { ctx._source.dsm.#propertyName.addAll(params.dsm.#propertyName) }";

    public AddListToNestedScriptBuilder(String propertyName) {
        super(propertyName, StringUtils.EMPTY, SCRIPT);
    }
}
