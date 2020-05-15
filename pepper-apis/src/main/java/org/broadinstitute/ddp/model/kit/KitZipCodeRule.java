package org.broadinstitute.ddp.model.kit;

import java.util.HashSet;
import java.util.Set;

import org.jdbi.v3.core.Handle;

public class KitZipCodeRule extends KitRule<String> {

    private final Set<String> zipCodes;

    public KitZipCodeRule(long id, Set<String> zipCodes) {
        super(id, KitRuleType.ZIP_CODE);
        this.zipCodes = new HashSet<>(zipCodes);
    }

    @Override
    public boolean validate(Handle handle, String inputZipCode) {
        return inputZipCode != null && zipCodes.contains(inputZipCode.trim());
    }
}
