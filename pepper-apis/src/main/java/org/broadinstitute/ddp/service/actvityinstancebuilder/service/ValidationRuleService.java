package org.broadinstitute.ddp.service.actvityinstancebuilder.service;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.jdbi.v3.core.Handle;

public class ValidationRuleService {

    private static final I18nContentRenderer i18nContentRenderer = new I18nContentRenderer();

    public String detectValidationRuleMessage(
            Handle handle, RuleType ruleType, Long hintTemplateId, long langCodeId, long timestamp) {
        String correctionHint = null;
        if (hintTemplateId != null) {
            correctionHint = i18nContentRenderer.renderContent(handle, hintTemplateId, langCodeId, timestamp);
        }
        if (correctionHint != null) {
            return correctionHint;
        } else {
            var validationDao = handle.attach(ValidationDao.class);
            return validationDao.getJdbiI18nValidationMsgTrans().getValidationMessage(
                    validationDao.getJdbiValidationType().getTypeId(ruleType), langCodeId);
        }
    }

    @FunctionalInterface
    public interface ValidationRuleMessageDetector {

        String detectValidationRuleMessage(
                Handle handle, RuleType ruleType, Long hintTemplateId, long langCodeId, long timestamp);
    }
}
