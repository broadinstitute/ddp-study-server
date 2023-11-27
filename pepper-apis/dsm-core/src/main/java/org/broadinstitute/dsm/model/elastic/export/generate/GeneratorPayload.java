package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.patch.Patch;

public class GeneratorPayload {

    private Patch patch;
    List<NameValue> nameValues;

    public GeneratorPayload() {
        nameValues = new ArrayList<>();
    }

    public GeneratorPayload(List<NameValue> nameValue, Patch patch) {
        this(patch);
        this.nameValues = nameValue;
    }

    public GeneratorPayload(NameValue nameValue, Patch patch) {
        this(patch);
        nameValues.add(0, nameValue);
    }

    private GeneratorPayload(Patch patch) {
        this();
        this.patch = patch;
    }

    public GeneratorPayload(List<NameValue> nameValue) {
        this.nameValues = nameValue;
    }

    public GeneratorPayload(NameValue nameValue) {
        this(nameValue, null);
    }


    public List<NameValue> getNameValues() {
        return nameValues;
    }

    public NameValue getNameValue() {
        return nameValues.get(0);
    }

    public String getName() {
        return nameValues.get(0).getName();
    }

    public Object getValue() {
        return nameValues.get(0).getValue();
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
