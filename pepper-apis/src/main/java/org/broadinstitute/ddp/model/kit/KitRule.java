package org.broadinstitute.ddp.model.kit;

import org.jdbi.v3.core.Handle;

public abstract class KitRule<T> {
    private KitRuleType type;

    KitRule(KitRuleType kitRuleType) {
        this.type = kitRuleType;
    }

    public KitRuleType getKitRuleType() {
        return type;
    }


    public abstract boolean validate(Handle handle, T input);
}
