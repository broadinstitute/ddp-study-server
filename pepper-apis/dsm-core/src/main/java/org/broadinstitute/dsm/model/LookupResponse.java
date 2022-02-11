package org.broadinstitute.dsm.model;

import lombok.Data;
import org.broadinstitute.dsm.model.NameValue;

@Data
public class LookupResponse {

    private final NameValue field1;
    private final NameValue field2;
    private final NameValue field3;
    private final NameValue field4;
    private final NameValue field5;

    public LookupResponse(String name) {
        this(name, null, null, null, null);
    }

    public LookupResponse(String name, String contact, String phone, String fax, String destructionPolicy) {
        this("field1", name, "field2", contact, "field3", phone, "field4", fax, "field5", destructionPolicy);
    }

    public LookupResponse(String name1, String value1, String name2, String value2, String name3, String value3,
                          String name4, String value4, String name5, String value5) {
        this.field1 = new NameValue(name1, value1);
        this.field2 = new NameValue(name2, value2);
        this.field3 = new NameValue(name3, value3);
        this.field4 = new NameValue(name4, value4);
        this.field5 = new NameValue(name5, value5);
    }
}
