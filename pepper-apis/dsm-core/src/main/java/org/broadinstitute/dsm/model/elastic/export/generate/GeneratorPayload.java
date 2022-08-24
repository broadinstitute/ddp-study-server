package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.patch.Patch;

public class GeneratorPayload {

    private Patch patch;
    NameValue nameValue;

    public GeneratorPayload() {
    }

    public GeneratorPayload(NameValue nameValue, Patch patch) {
        this.nameValue = nameValue;
        this.patch = patch;
    }

    public GeneratorPayload(NameValue nameValue) {
        this.nameValue = nameValue;
    }

    public NameValue getNameValue() {
        return nameValue;
    }

    public String getName() {
        return nameValue.getName();
    }

    public Object getValue() {
        return nameValue.getValue();
    }

    public Integer getRecordId() {
        return Integer.valueOf(patch.getId());
    }

    public String getParent() {
        return patch.getParent();
    }

    public String getParentId() {
        return patch.getParentId();
    }

    public String getCamelCaseFieldName() {
        return CamelCaseConverter.of(Util.getDBElement(getName()).getColumnName()).convert();
    }

    public String getRawFieldName() {
        return Util.getDBElement(getName()).getColumnName();
    }

    public String getInstanceName() {
        return patch.getRealm();
    }
}