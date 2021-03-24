package org.broadinstitute.ddp.handlers.util;
import org.broadinstitute.ddp.util.CheckValidity;

import java.util.ArrayList;


public class ListPayload extends ArrayList<Payload> implements CheckValidity {

    public ListPayload(){}

    public boolean isValid() {
        return true;
    }
}
