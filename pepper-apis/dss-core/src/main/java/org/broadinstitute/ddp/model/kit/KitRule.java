package org.broadinstitute.ddp.model.kit;

import org.jdbi.v3.core.Handle;

public abstract class KitRule<T> {

    private long id;
    private KitRuleType type;

    KitRule(long id, KitRuleType kitRuleType) {
        this.id = id;
        this.type = kitRuleType;
    }

    public long getId() {
        return id;
    }

    public KitRuleType getType() {
        return type;
    }

    public abstract boolean validate(Handle handle, T input);
}
