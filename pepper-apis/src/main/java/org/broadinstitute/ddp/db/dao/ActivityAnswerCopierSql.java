package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.copyanswer.AgreementAnswerCopier;
import org.broadinstitute.ddp.db.dao.copyanswer.AnswerCopier;
import org.broadinstitute.ddp.db.dao.copyanswer.AnswerCopyResult;
import org.broadinstitute.ddp.db.dao.copyanswer.BooleanAnswerCopier;
import org.broadinstitute.ddp.db.dao.copyanswer.CompositeAnswerCopier;
import org.broadinstitute.ddp.db.dao.copyanswer.CompositeAnswerCopyConfiguration;
import org.broadinstitute.ddp.db.dao.copyanswer.DateAnswerCopier;
import org.broadinstitute.ddp.db.dao.copyanswer.NumericAnswerCopier;
import org.broadinstitute.ddp.db.dao.copyanswer.PicklistAnswerCopier;
import org.broadinstitute.ddp.db.dao.copyanswer.TextAnswerCopier;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ActivityAnswerCopierSql extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(ActivityAnswerCopierSql.class);

    @UseStringTemplateSqlLocator
    @SqlQuery("getLatestAnswer")
    @RegisterConstructorMapper(AnswerToCopy.class)
    AnswerToCopy getLatestAnswer(@Bind("questionStableId") String sourceQuestionStableId,
                         @Bind("activityInstanceId") long sourceInstanceId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("copyTextAnswer")
    void copyTextAnswer(@Bind("sourceAnswerId") long answerId, @Bind("destinationAnswerId") long destinationAnswerId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("copyDateAnswer")
    int copyDateAnswer(@Bind("sourceAnswerId") long sourceAnswerId, @Bind("destinationAnswerId") long destinationAnswerId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("copyPicklistAnswer")
    int copyPicklistAnswer(@Bind("sourceAnswerId") long sourceAnswerId, @Bind("destinationAnswerId") long destinationAnswerId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("copyBooleanAnswer")
    int copyBooleanAnswer(@Bind("sourceAnswerId") long sourceAnswerId, @Bind("destinationAnswerId") long destinationAnswerId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("copyAgreementAnswer")
    int copyAgreementAnswer(@Bind("sourceAnswerId") long sourceAnswerId, @Bind("destinationAnswerId") long destinationAnswerId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("copyNumericAnswer")
    int copyNumericAnswer(@Bind("sourceAnswerId") long sourceAnswerId, @Bind("destinationAnswerId") long destinationAnswerId);

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("baseActivityAnswerCopy")
    long copyBaseAnswer(@Bind("sourceAnswerId") long sourceAnswerId,
                        @Bind("answerGuid") String newAnswerGuid,
                        @Bind("destinationStableId") String destinationQuestionStableId,
                        @Bind("destinationActivityInstanceId") long destinationInstanceId,
                        @Bind("createdAt") long createdAtEpochMillis,
                        @Bind("lastUpdatedAt") long lastUpdatedAtEpochMillis);

    /**
     * Copies the base data from the answer table into a new row
     * @return the newly generated answer_id
     */
    default AnswerCopyResult copyBaseAnswer(String sourceQuestionStableId,
                                            String destinationQuestionStableId,
                                            long sourceInstanceId,
                                            long destinationInstanceId,
                                            long createdAtEpochMillis,
                                            long lastUpdatedAtEpochMillis) {

        String newAnswerGuid = DBUtils.uniqueStandardGuid(getHandle(), SqlConstants.AnswerTable.TABLE_NAME,
                SqlConstants.AnswerTable.GUID);
        AnswerToCopy answerToCopy = getLatestAnswer(sourceQuestionStableId, sourceInstanceId);

        long newAnswerId = copyBaseAnswer(answerToCopy.getAnswerId(), newAnswerGuid, destinationQuestionStableId,
                destinationInstanceId, createdAtEpochMillis, lastUpdatedAtEpochMillis);

        return new AnswerCopyResult(answerToCopy.getAnswerId(), newAnswerId);
    }

    default AnswerCopyResult copyAnswer(String sourceQuestionStableId,
                                                      String destinationQuestionStableId,
                                                      long sourceInstanceId,
                                                      long destinationInstanceId,
                                                      long createdAtEpochMillis,
                                                      long lastUpdatedAtEpochMillis,
                                                      CompositeAnswerCopyConfiguration compositeCopyConfiguration) {
        AnswerToCopy answerToCopy = getLatestAnswer(sourceQuestionStableId, sourceInstanceId);

        AnswerCopier answerCopier = null;

        switch (answerToCopy.getQuestionType()) {
            case TEXT: {
                answerCopier = new TextAnswerCopier();
                break;
            } case COMPOSITE: {
                answerCopier = new CompositeAnswerCopier();
                break;
            } case PICKLIST: {
                answerCopier = new PicklistAnswerCopier();
                break;
            } case DATE: {
                answerCopier = new DateAnswerCopier();
                break;
            } case AGREEMENT: {
                answerCopier = new AgreementAnswerCopier();
                break;
            } case BOOLEAN: {
                answerCopier = new BooleanAnswerCopier();
                break;
            } case NUMERIC: {
                answerCopier = new NumericAnswerCopier();
                break;
            }
            default: {
                throw new DaoException(answerToCopy.getQuestionType() + " question type does not have copy support");
            }
        }

        return answerCopier.copyAnswer(getHandle(),
                sourceInstanceId, sourceQuestionStableId,
                destinationInstanceId, destinationQuestionStableId,
                createdAtEpochMillis,
                lastUpdatedAtEpochMillis, compositeCopyConfiguration);
    }

    class AnswerToCopy {

        private long answerId;

        private QuestionType questionType;

        @JdbiConstructor
        public AnswerToCopy(@ColumnName("answer_id") long answerId,
                            @ColumnName("question_type_code") QuestionType questionType) {
            this.answerId = answerId;
            this.questionType = questionType;
        }

        public long getAnswerId() {
            return answerId;
        }

        public QuestionType getQuestionType() {
            return questionType;
        }
    }

}
