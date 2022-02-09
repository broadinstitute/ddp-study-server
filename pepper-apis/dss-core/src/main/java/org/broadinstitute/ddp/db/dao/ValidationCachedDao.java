package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.core.Handle;

public class ValidationCachedDao extends SQLObjectWrapper<ValidationDao> implements ValidationDao  {

    public ValidationCachedDao(Handle handle) {
        super(handle, ValidationDao.class);
    }

    @Override
    public JdbiValidation getJdbiValidation() {
        return delegate.getJdbiValidation();
    }

    @Override
    public JdbiValidationType getJdbiValidationType() {
        return delegate.getJdbiValidationType();
    }

    @Override
    public JdbiRegexValidation getJdbiRegexValidation() {
        return delegate.getJdbiRegexValidation();
    }

    @Override
    public JdbiLengthValidation getJdbiLengthValidation() {
        return delegate.getJdbiLengthValidation();
    }

    @Override
    public JdbiNumOptionsSelectedValidation getJdbiNumOptionsSelectedValidation() {
        return delegate.getJdbiNumOptionsSelectedValidation();
    }

    @Override
    public JdbiDateRangeValidation getJdbiDateRangeValidation() {
        return delegate.getJdbiDateRangeValidation();
    }

    @Override
    public JdbiAgeRangeValidation getJdbiAgeRangeValidation() {
        return delegate.getJdbiAgeRangeValidation();
    }

    @Override
    public JdbiIntRangeValidation getJdbiIntRangeValidation() {
        return delegate.getJdbiIntRangeValidation();
    }

    @Override
    public JdbiQuestionValidation getJdbiQuestionValidation() {
        return new JdbiQuestionValidationCached(getHandle());
    }

    @Override
    public JdbiRevision getJdbiRevision() {
        return delegate.getJdbiRevision();
    }

    @Override
    public TemplateDao getTemplateDao() {
        return delegate.getTemplateDao();
    }

    @Override
    public JdbiI18nValidationMsgTrans getJdbiI18nValidationMsgTrans() {
        return delegate.getJdbiI18nValidationMsgTrans();
    }
}
