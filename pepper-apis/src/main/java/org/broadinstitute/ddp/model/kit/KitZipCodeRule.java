package org.broadinstitute.ddp.model.kit;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.jdbi.v3.core.Handle;

public class KitZipCodeRule extends KitRule<String> {

    public static final Pattern ZIP_PATTERN = Pattern.compile("\\d{5}(-\\d{4})?");

    private final Set<String> zipCodes;

    public KitZipCodeRule(long id, Set<String> zipCodes) {
        super(id, KitRuleType.ZIP_CODE);
        this.zipCodes = new HashSet<>(zipCodes);
    }

    public Set<String> getZipCodes() {
        return Set.copyOf(zipCodes);
    }

    public void addZipCode(String zipCode) {
        this.zipCodes.add(zipCode);
    }

    @Override
    public boolean validate(Handle handle, String inputZipCode) {
        if (inputZipCode == null) {
            return false;
        }
        String normalizedZip = inputZipCode.trim();
        if (ZIP_PATTERN.matcher(normalizedZip).matches()) {
            normalizedZip = normalizedZip.split("-")[0];
        }
        return zipCodes.contains(normalizedZip);
    }
}
