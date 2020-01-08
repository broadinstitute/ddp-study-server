package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.broadinstitute.ddp.db.dto.ChildAnswerDto;
import org.broadinstitute.ddp.db.dto.CompositeAnswerSummaryDto;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiCompositeAnswer extends SqlObject {

    @UseStringTemplateSqlLocator()
    @SqlBatch("insertCompositeAnswerItem")
    @GetGeneratedKeys()
    long[] insertChildAnswerItems(@Bind("parentAnswerId") long parentAnswerId,
                                  @Bind("answerId") List<Long> childAnswerId, @Bind("orderIdx") List<Integer> orderIndex);

    /**
     * Will insert the child answer references
     * Method will flatten out the necessary fields so we can enter all as one batch
     *
     * @param parentAnswerId the parent answer id
     * @param rowsOfChildIds the ids for the child answers
     * @return a count of all inserts done
     */
    default long[] insertChildAnswerItems(long parentAnswerId, List<List<Long>> rowsOfChildIds) {
        List<Integer> rowIdxColl = new ArrayList<>();
        int currentRowIdx = 0;
        List<Long> flattenedListOfIds = new ArrayList<>();
        for (List<Long> rowOfChildIds : rowsOfChildIds) {
            //Let's leave out any nulls. They don't really help us
            List<Long> nonNullIds = rowOfChildIds.stream().filter(Objects::nonNull).collect(toList());
            flattenedListOfIds.addAll(nonNullIds);
            for (int i = 0; i < nonNullIds.size(); i++) {
                rowIdxColl.add(currentRowIdx);
            }
            ++currentRowIdx;
        }
        return insertChildAnswerItems(parentAnswerId, flattenedListOfIds, rowIdxColl);
    }

    @UseStringTemplateSqlLocator()
    @SqlUpdate("deleteCompositeAnswerItemsByParentId")
    int deleteChildAnswerItems(@Bind("parentAnswerId") long parentAnswerId);


    default Optional<CompositeAnswerSummaryDto> findCompositeAnswerSummary(long parentId) {
        AtomicInteger currentRowIdx = new AtomicInteger(-1);
        String query = StringTemplateSqlLocator.findStringTemplate(JdbiCompositeAnswer.class,
                "getCompositeAnswerSummary")
                .render();
        CompositeAnswerSummaryDto answerSummary = getHandle().createQuery(query)
                .bind("parentAnswerId", parentId)
                .reduceResultSet(new CompositeAnswerSummaryDto(),
                        (parentAnswer, rs, ctx) -> {
                            if (parentAnswer.getGuid() == null) {
                                parentAnswer.setId(rs.getLong("parent_answer_id"));
                                parentAnswer.setGuid(rs.getString("parent_answer_guid"));
                                parentAnswer.setQuestionStableId(rs.getString("parent_question_stable_id"));
                            }
                            int rowIdx = rs.getInt("row_order");
                            //if this is null it means we are just seeing a row for the question definition
                            if (!rs.wasNull()) {
                                //could be the outer-join NULL row-order value
                                List<ChildAnswerDto> lastChildRowAnswerDtos;
                                if (rowIdx != (currentRowIdx.get())) {
                                    currentRowIdx.set(rowIdx);
                                    lastChildRowAnswerDtos = new ArrayList<>();
                                    parentAnswer.getChildAnswers().add(lastChildRowAnswerDtos);
                                } else {
                                    lastChildRowAnswerDtos = parentAnswer.getLastRowOfChildrenAnswers();
                                }
                                Long childAnswerId = (Long) rs.getObject("child_answer_id");

                                lastChildRowAnswerDtos
                                        .add(new ChildAnswerDto(childAnswerId,
                                                rs.getString("child_answer_guid"),
                                                rs.getLong("child_question_id"),
                                                rs.getString("child_question_stable_id"),
                                                QuestionType.valueOf(rs.getString("child_question_type_code")),
                                                rs.getLong("child_answer_created_at"),
                                                rs.getLong("child_answer_last_updated_at"),
                                                (Integer)rs.getObject("row_order")));
                        }
                            return parentAnswer;
                        });
        return Optional.ofNullable(answerSummary.getGuid() == null ? null : answerSummary);
    }
}
