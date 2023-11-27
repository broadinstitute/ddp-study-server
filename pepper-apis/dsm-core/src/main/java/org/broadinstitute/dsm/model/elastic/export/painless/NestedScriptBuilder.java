package org.broadinstitute.dsm.model.elastic.export.painless;

public class NestedScriptBuilder extends BaseScriptBuilder {
    protected String uniqueIdentifier;

    protected String script;

    public NestedScriptBuilder(String propertyName, String uniqueIdentifier, String script) {
        super(propertyName);
        this.uniqueIdentifier = uniqueIdentifier;
        this.script = script;
    }

    public NestedScriptBuilder(String script) {
        this.script = script;
    }

    @Override
    public void setUniqueIdentifier(String uniqueIdentifier) {
        this.uniqueIdentifier = uniqueIdentifier;
    }

    @Override
    public String build() {
        return script.replace("#propertyName", propertyName).replace("#uniqueIdentifier", uniqueIdentifier);
    }
}
