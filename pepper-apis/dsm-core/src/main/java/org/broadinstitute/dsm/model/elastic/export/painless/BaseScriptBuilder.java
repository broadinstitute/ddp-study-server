package org.broadinstitute.dsm.model.elastic.export.painless;

public abstract class BaseScriptBuilder implements ScriptBuilder {

    protected final String propertyName;

    public BaseScriptBuilder(String propertyName) {
        this.propertyName = propertyName;
    }
}
