package org.broadinstitute.dsm.model.elastic.export.painless;

public abstract class BaseScriptBuilder implements ScriptBuilder {

    protected String propertyName;

    public BaseScriptBuilder(String propertyName) {
        this.propertyName = propertyName;
    }

    public BaseScriptBuilder() {}

    @Override
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }
}
