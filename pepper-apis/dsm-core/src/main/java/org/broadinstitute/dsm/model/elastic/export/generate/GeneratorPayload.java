package org.broadinstitute.dsm.model.elastic.export.generate;

import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.elastic.Util;

public class GeneratorPayload {

    NameValue nameValue;
    int recordId;
    private String parent;
    private String parentId;

    public GeneratorPayload() {}

    public GeneratorPayload(NameValue nameValue, int recordId) {
        this.nameValue = nameValue;
        this.recordId = recordId;
    }

    public GeneratorPayload(NameValue nameValue, int recordId, String parent, String parentId) {
        this(nameValue, recordId);
        this.parent = parent;
        this.parentId = parentId;
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

    public int getRecordId() {
        return recordId;
    }

    public String getParent() {
        return parent;
    }

    public String getParentId() {
        return parentId;
    }

    public String getCamelCaseFieldName() {
        return Util.underscoresToCamelCase(Util.getDBElement(getName()).getColumnName());
    }

    public String getRawFieldName() {
        return Util.getDBElement(getName()).getColumnName();
    }
}
