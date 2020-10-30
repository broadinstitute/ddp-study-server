package org.broadinstitute.ddp.model.kit;

import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

public class KitPexRule extends KitRule<String> {

    PexInterpreter pexInterpreter;
    String pexExpression;

    public KitPexRule(long id, PexInterpreter pexInterpreter, String pexExpression) {
        super(id, KitRuleType.PEX);
        this.pexInterpreter = pexInterpreter;
        this.pexExpression = pexExpression;
    }

    @Override
    public boolean validate(Handle handle, String userGuid) {
        throw new UnsupportedOperationException("Activity instance guid required to run PEX");
    }


    public boolean validate(Handle handle, String userGuid, String activityInstanceGuid) {
        return pexInterpreter.eval(pexExpression, handle, userGuid, userGuid, activityInstanceGuid);
    }
}
