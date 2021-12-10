package org.broadinstitute.lddp.handlers.util;
import org.broadinstitute.lddp.util.CheckValidity;

import java.util.ArrayList;


public class ListPayload extends ArrayList<Payload> implements CheckValidity {

    public ListPayload(){}

    public boolean isValid() {
        return true;
    }
}
