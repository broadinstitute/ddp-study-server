package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.NonNull;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityAnswerCopierSql.CompositeAnswerCopier.CompositeAnswerCopyConfiguration;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.CompositeAnswerSummaryDto;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.Handle;
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

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("baseActivityAnswerCopy")
    long copyBaseAnswer(@Bind("sourceAnswerId") long sourceAnswerId,
                         @Bind("answerGuid") String newAnswerGuid,
                         @Bind("destinationStableId") String destinationQuestionStableId,
                         @Bind("destinationActivityInstanceId") long destinationInstanceId,
                         @Bind("createdAt") long createdAtEpochMillis,
                         @Bind("lastUpdatedAt") long lastUpdatedAtEpochMillis);

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

    // todo arz AnswerCopier class, with different state, and copyTo(...) like below?

    // config for composite has sourceChildQuestion -> destChildQuestion


    /**
     * Copies the base data from the answer table into a new row
     * @return the newly generated answer_id
     */
    default AnswerCopyResult copyBaseAnswer(String sourceQuestionStableId,
                                String destinationQuestionStableId,
                                long sourceInstanceId,
                                long destinationInstanceId,
                                long createdAtEpochMillis,
                                long lastUpdatedAtEpochMillis,
                                CompositeAnswerCopyConfiguration compositeCopyConfig) {

        String newAnswerGuid = DBUtils.uniqueStandardGuid(getHandle(), SqlConstants.AnswerTable.TABLE_NAME,
                SqlConstants.AnswerTable.GUID);
        AnswerToCopy answerToCopy = getLatestAnswer(sourceQuestionStableId, sourceInstanceId);

        long newAnswerId = copyBaseAnswer(answerToCopy.getAnswerId(), newAnswerGuid, destinationQuestionStableId,
                destinationInstanceId, createdAtEpochMillis, lastUpdatedAtEpochMillis);


        return new AnswerCopyResult(answerToCopy.getAnswerId(), newAnswerId);
    }

    default <T extends AnswerCopyResult> T copyAnswer(String sourceQuestionStableId,
                                                      String destinationQuestionStableId,
                                                      long sourceInstanceId,
                                                      long destinationInstanceId,
                                                      long createdAtEpochMillis,
                                                      long lastUpdatedAtEpochMillis,
                                                      CompositeAnswerCopyConfiguration compositeCopyConfiguration) {

        long start = System.currentTimeMillis();
        AnswerToCopy answerToCopy = getLatestAnswer(sourceQuestionStableId, sourceInstanceId);

        AnswerCopier answerCopier = null;

        switch (answerToCopy.getQuestionType()) {
            case TEXT: {
                answerCopier = new TextAnswerCopier();
                break;
            } case COMPOSITE: {
                answerCopier = new CompositeAnswerCopier();
                break;
            }
        }

        if (answerCopier != null) {
            return (T)answerCopier.copyAnswer(getHandle(),
                    sourceQuestionStableId,
                    destinationQuestionStableId,
                    sourceInstanceId,
                    destinationInstanceId,
                    createdAtEpochMillis,
                    lastUpdatedAtEpochMillis, compositeCopyConfiguration);
        } else {
            String newAnswerGuid = DBUtils.uniqueStandardGuid(getHandle(), SqlConstants.AnswerTable.TABLE_NAME,
                    SqlConstants.AnswerTable.GUID);

            AnswerCopyResult answerCopyResult = copyBaseAnswer(sourceQuestionStableId, destinationQuestionStableId, sourceInstanceId, destinationInstanceId,
                    createdAtEpochMillis, lastUpdatedAtEpochMillis, compositeCopyConfiguration);


            // todo arz replace with switch
            if (answerToCopy.getQuestionType() == QuestionType.DATE) {
                copyDateAnswer(answerToCopy.getAnswerId(), answerCopyResult.getDestinationAnswerId());
            } else if (answerToCopy.getQuestionType() == QuestionType.PICKLIST) {
                copyPicklistAnswer(answerToCopy.getAnswerId(), answerCopyResult.getDestinationAnswerId());
            } else if (answerToCopy.getQuestionType() == QuestionType.BOOLEAN) {
                copyBooleanAnswer(answerToCopy.getAnswerId(), answerCopyResult.getDestinationAnswerId());
            } else if (answerToCopy.getQuestionType() == QuestionType.AGREEMENT) {
                copyAgreementAnswer(answerToCopy.getAnswerId(), answerCopyResult.getDestinationAnswerId());
            } else if (answerToCopy.getQuestionType() == QuestionType.NUMERIC) {
                copyNumericAnswer(answerToCopy.getAnswerId(), answerCopyResult.getDestinationAnswerId());
            } else {
                throw new DaoException(answerToCopy.getQuestionType() + " question type does not have copy support");
            }

            LOG.info("It took " + (System.currentTimeMillis() - start) + "ms to copy " + sourceQuestionStableId);
            return (T)answerCopyResult;
        }
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

    /**
     * An output from copying an answer, which includes
     * the source answer id and the destination id at a minimum.
     */
    class AnswerCopyResult {

        private final long sourceAnswerId;

        private final long destinationAnswerId;

        public AnswerCopyResult(long sourceAnswerId, long destinationAnswerId) {
            this.sourceAnswerId = sourceAnswerId;
            this.destinationAnswerId = destinationAnswerId;
        }

        public long getSourceAnswerId() {
            return sourceAnswerId;
        }

        public long getDestinationAnswerId() {
            return destinationAnswerId;
        }
    }

    /**
     * Extension to {@link AnswerCopyResult} that adds
     * a {@link #responseOrder}.
     */
    class CompositeChildAnswerCopyResult extends AnswerCopyResult {

        private final int responseOrder;

        public CompositeChildAnswerCopyResult(long sourceChildAnswerId, long destinationChildAnswerId, int responseOrder) {
            super(sourceChildAnswerId, destinationChildAnswerId);
            this.responseOrder = responseOrder;
        }

        public int getResponseOrder() {
            return responseOrder;
        }
    }

    interface AnswerCopier<T extends AnswerCopyResult> {

        /**
         * Copies the answer to one question in an activity instance
         * to a potentially different question in a different activity instance.
         * @param handle
         * @param sourceQuestionStableId
         * @param destinationQuestionStableId
         * @param sourceInstanceId
         * @param destinationInstanceId
         * @param createdAtEpochMillis
         * @param lastUpdatedAtEpochMillis
         * @param compositeCopyConfiguration implementations may set #parentAnswerQuestionId so that other copiers can keep track of
         *                                   composite answers
         * @return
         */
        T copyAnswer(Handle handle,
                     String sourceQuestionStableId,
                    String destinationQuestionStableId,
                    long sourceInstanceId,
                                    long destinationInstanceId,
                                    long createdAtEpochMillis,
                                    long lastUpdatedAtEpochMillis,
                     CompositeAnswerCopyConfiguration compositeCopyConfiguration);
    }

    /**
     * Copies top-level answer and composite_answer_item (when directed by compositeCopyConfig)
     */
    abstract class BaseAnswerCopier<T extends AnswerCopyResult> implements AnswerCopier {


        public T copyAnswer(Handle handle, String sourceQuestionStableId, String destinationQuestionStableId,
                                           long sourceInstanceId, long destinationInstanceId, long createdAtEpochMillis,
                            long lastUpdatedAtEpochMillis, CompositeAnswerCopyConfiguration compositeCopyConfig) {
            return (T)handle.attach(ActivityAnswerCopierSql.class).copyBaseAnswer(
                    sourceQuestionStableId,
                    destinationQuestionStableId,
                    sourceInstanceId,
                    destinationInstanceId,
                    createdAtEpochMillis,
                    lastUpdatedAtEpochMillis,
                    compositeCopyConfig);
        }
    }

    class TextAnswerCopier extends BaseAnswerCopier<AnswerCopyResult> {

        @Override
        public AnswerCopyResult copyAnswer(Handle handle, String sourceQuestionStableId, String destinationQuestionStableId,
                                           long sourceInstanceId, long destinationInstanceId, long createdAtEpochMillis,
                                           long lastUpdatedAtEpochMillis,
                                           CompositeAnswerCopyConfiguration compositeCopyConfiguration) {
            AnswerCopyResult baseCopyResult = super.copyAnswer(handle, sourceQuestionStableId, destinationQuestionStableId, sourceInstanceId,
                    destinationInstanceId, createdAtEpochMillis, lastUpdatedAtEpochMillis, compositeCopyConfiguration);

            handle.attach(ActivityAnswerCopierSql.class).copyTextAnswer(baseCopyResult.getSourceAnswerId(),
                    baseCopyResult.getDestinationAnswerId());

            return baseCopyResult;
        }
    }



    class CompositeAnswerCopyResult extends AnswerCopyResult {

        public CompositeAnswerCopyResult(long sourceParentAnswerId,
                                         long destinationParentAnswerId,
                                         List<CompositeChildAnswerCopyResult> childAnswerMapping) {
            super(sourceParentAnswerId, destinationParentAnswerId);
        }
    }

    class CompositeAnswerCopier extends BaseAnswerCopier<CompositeAnswerCopyResult> {

        @Override
        public CompositeAnswerCopyResult copyAnswer(Handle handle,
                               String sourceQuestionStableId,
                               String destinationQuestionStableId,
                               long sourceInstanceId,
                               long destinationInstanceId,
                               long createdAtEpochMillis,
                               long lastUpdatedAtEpochMillis,
                            CompositeAnswerCopyConfiguration compositeCopyConfiguration) {

            AnswerCopyResult parentCopyResult = super.copyAnswer(handle,
                    sourceQuestionStableId,
                    destinationQuestionStableId,
                    sourceInstanceId,
                    destinationInstanceId,
                    createdAtEpochMillis,
                    lastUpdatedAtEpochMillis,
                    compositeCopyConfiguration);

            long sourceParentAnswerId = parentCopyResult.getSourceAnswerId();
            // copy child answers
            ActivityAnswerCopierSql answerCopier = handle.attach(ActivityAnswerCopierSql.class);
            JdbiCompositeAnswer jdbiCompositeAnswer = handle.attach(JdbiCompositeAnswer.class);
            List<CompositeChildAnswerCopyResult> childAnswerCopyResults = new ArrayList<>();

            compositeCopyConfiguration.setCompositeParentAnswerId(parentCopyResult.getDestinationAnswerId());

            // cache the response_order for subsequent copying
            Optional<CompositeAnswerSummaryDto> sourceChildAnswers = handle.attach(JdbiCompositeAnswer.class).findCompositeAnswerSummary(sourceParentAnswerId);

            Map<Long, Integer> answerIdToOrderIndex = new HashMap<>();
            sourceChildAnswers.ifPresent(child -> {
                child.getChildAnswers().stream().forEach(row -> {
                    row.stream().forEach(childAnswer -> {
                        answerIdToOrderIndex.put(childAnswer.getId(), childAnswer.getOrderIndex());
                    });
                });
            });

            for (Map.Entry<String, String> childAnswerConfig : compositeCopyConfiguration.getChildCopyConfiguration().entrySet()) {
                String sourceChildStableId = childAnswerConfig.getKey();
                String destinationChildStableId = childAnswerConfig.getValue();

                AnswerCopyResult childAnswerCopyResult = answerCopier.copyAnswer(sourceChildStableId, destinationChildStableId,
                        sourceInstanceId, destinationInstanceId, createdAtEpochMillis, lastUpdatedAtEpochMillis, compositeCopyConfiguration);

                long[] numRowsInserted = jdbiCompositeAnswer.insertChildAnswerItems(parentCopyResult.getDestinationAnswerId(),
                        Collections.singletonList(childAnswerCopyResult.getDestinationAnswerId()),
                        Collections.singletonList(answerIdToOrderIndex.get(childAnswerCopyResult.getSourceAnswerId())));


                if (numRowsInserted.length != 1 && numRowsInserted[0] != 1) {
                    throw new DaoException("Inserted " + numRowsInserted.length + " batches, first one of size " + numRowsInserted[0]
                            + " while copying answer from "
                            + childAnswerCopyResult.getSourceAnswerId() + " to " + childAnswerCopyResult.getDestinationAnswerId());
                }
            }
            return new CompositeAnswerCopyResult(parentCopyResult.getSourceAnswerId(), parentCopyResult.getDestinationAnswerId(),
                    childAnswerCopyResults);

        }

        static class CompositeAnswerCopyConfiguration {

            private final Map<String, String> sourceToDestination = new HashMap<>();
            private long compositeParentAnswerId;

            public void addChildCopyConfiguration(String sourceStableId, String destinationStableId) {
                sourceToDestination.put(sourceStableId, destinationStableId);
            }

            public Map<String, String> getChildCopyConfiguration() {
                return Collections.unmodifiableMap(sourceToDestination);
            }

            public void setCompositeParentAnswerId(long compositeParentAnswerId) {
                this.compositeParentAnswerId = compositeParentAnswerId;
            }

            public long getCompositeParentAnswerId() {
                return compositeParentAnswerId;
            }
        }
    }
}
