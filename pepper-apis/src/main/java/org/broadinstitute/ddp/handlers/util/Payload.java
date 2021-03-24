package org.broadinstitute.ddp.handlers.util;

import org.broadinstitute.ddp.util.CheckValidity;

import java.util.HashMap;

public class Payload  extends HashMap<String, Object> implements CheckValidity{

    public Payload(){}

    public boolean isValid() {
        return true;
    }
}
