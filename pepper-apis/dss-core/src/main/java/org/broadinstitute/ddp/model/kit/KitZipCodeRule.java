package org.broadinstitute.ddp.model.kit;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class KitZipCodeRule extends KitRule<String> {

    public static final Pattern ZIP_PATTERN = Pattern.compile("\\d{5}(-\\d{4})?");

    private final Set<String> zipCodes;
    private final Long errorMessageTemplateId;
    private final Long warningMessageTemplateId;

    @JdbiConstructor
    public KitZipCodeRule(
            @ColumnName("kit_rule_id") long id,
            @ColumnName("error_message_template_id") Long errorMessageTemplateId,
            @ColumnName("warning_message_template_id") Long warningMessageTemplateId) {
        super(id, KitRuleType.ZIP_CODE);
        this.zipCodes = new HashSet<>();
        this.errorMessageTemplateId = errorMessageTemplateId;
        this.warningMessageTemplateId = warningMessageTemplateId;
    }

    public KitZipCodeRule(long id, Set<String> zipCodes) {
        super(id, KitRuleType.ZIP_CODE);
        this.zipCodes = new HashSet<>(zipCodes);
        this.errorMessageTemplateId = null;
        this.warningMessageTemplateId = null;
    }

    public Set<String> getZipCodes() {
        return Set.copyOf(zipCodes);
    }

    public void addZipCode(String zipCode) {
        this.zipCodes.add(zipCode);
    }

    public Long getErrorMessageTemplateId() {
        return errorMessageTemplateId;
    }

    public Long getWarningMessageTemplateId() {
        return warningMessageTemplateId;
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
