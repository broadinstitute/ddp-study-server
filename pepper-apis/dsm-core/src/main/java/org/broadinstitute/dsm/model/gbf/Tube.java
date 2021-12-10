package org.broadinstitute.dsm.model.gbf;

import javax.xml.bind.annotation.XmlAttribute;

public class Tube {

    private String serial;

    public Tube() {
    }

    public Tube(String serial) {
        this.serial = serial;
    }

    @XmlAttribute(name="Serial")
    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }
}
