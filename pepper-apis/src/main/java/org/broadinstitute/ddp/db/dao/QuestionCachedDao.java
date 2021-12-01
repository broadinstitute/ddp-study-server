package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.core.Handle;

public class QuestionCachedDao extends SQLObjectWrapper<QuestionDao> implements QuestionDao {
    public QuestionCachedDao(Handle handle) {
        super(handle, QuestionDao.class);
    }

    @Override
    public JdbiActivity getJdbiActivity() {
        return delegate.getJdbiActivity();
    }

    @Override
    public JdbiQuestionStableCode getJdbiQuestionStableCode() {
        return delegate.getJdbiQuestionStableCode();
    }

    @Override
    public JdbiQuestion getJdbiQuestion() {
        return new JdbiQuestionCached(getHandle());
    }

    @Override
    public JdbiQuestionType getJdbiQuestionType() {
        return delegate.getJdbiQuestionType();
    }

    @Override
    public JdbiBooleanQuestion getJdbiBooleanQuestion() {
        return delegate.getJdbiBooleanQuestion();
    }

    @Override
    public JdbiTextQuestion getJdbiTextQuestion() {
        return delegate.getJdbiTextQuestion();
    }

    @Override
    public JdbiTextQuestionSuggestion getJdbiTextQuestionSuggestion() {
        return delegate.getJdbiTextQuestionSuggestion();
    }

    @Override
    public JdbiActivityInstanceSelectQuestion getJdbiActivityInstanceSelectQuestion() {
        return delegate.getJdbiActivityInstanceSelectQuestion();
    }

    @Override
    public JdbiActivityInstanceSelectActivityCodes getJdbiActivityInstanceSelectActivityCodes() {
        return delegate.getJdbiActivityInstanceSelectActivityCodes();
    }

    @Override
    public JdbiDateQuestion getJdbiDateQuestion() {
        return delegate.getJdbiDateQuestion();
    }

    @Override
    public JdbiDateQuestionFieldOrder getJdbiDateQuestionFieldOrder() {
        return delegate.getJdbiDateQuestionFieldOrder();
    }

    @Override
    public JdbiDateFieldType getJdbiDateFieldType() {
        return delegate.getJdbiDateFieldType();
    }

    @Override
    public JdbiDateRenderMode getJdbiDateRenderMode() {
        return delegate.getJdbiDateRenderMode();
    }

    @Override
    public JdbiDateQuestionMonthPicklist getJdbiDateQuestionMonthPicklist() {
        return delegate.getJdbiDateQuestionMonthPicklist();
    }

    @Override
    public JdbiDateQuestionYearPicklist getJdbiDateQuestionYearPicklist() {
        return delegate.getJdbiDateQuestionYearPicklist();
    }

    @Override
    public JdbiPicklistQuestion getJdbiPicklistQuestion() {
        return delegate.getJdbiPicklistQuestion();
    }

    @Override
    public JdbiCompositeQuestion getJdbiCompositeQuestion() {
        return delegate.getJdbiCompositeQuestion();
    }

    @Override
    public JdbiNumericQuestion getJdbiNumericQuestion() {
        return delegate.getJdbiNumericQuestion();
    }

    @Override
    public JdbiBlockQuestion getJdbiBlockQuestion() {
        return delegate.getJdbiBlockQuestion();
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
    public ValidationDao getValidationDao() {
        return new ValidationCachedDao(getHandle());
    }

    @Override
    public TemplateDao getTemplateDao() {
        return delegate.getTemplateDao();
    }

    @Override
    public PicklistQuestionDao getPicklistQuestionDao() {
        return new PicklistQuestionCachedDao(getHandle());
    }

    @Override
    public JdbiAgreementQuestion getJdbiAgreementQuestion() {
        return delegate.getJdbiAgreementQuestion();
    }

    @Override
    public AnswerDao getAnswerDao() {
        return new AnswerCachedDao(getHandle());
    }

    @Override
    public JdbiPicklistOption getJdbiPicklistOption() {
        return delegate.getJdbiPicklistOption();
    }

}
