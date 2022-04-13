package org.broadinstitute.dsm.model.patch;

import lombok.Getter;
import org.broadinstitute.dsm.model.NameValue;

@Getter
public class SMIDNameValue extends NameValue {

    private String type;

    public SMIDNameValue(String name, Object value, String type) {
        super(name, value);
        this.type = type;
    }

}
