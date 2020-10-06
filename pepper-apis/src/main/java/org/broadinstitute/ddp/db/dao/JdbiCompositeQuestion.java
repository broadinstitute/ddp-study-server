package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.IntStream;

import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiCompositeQuestion extends SqlObject {

    @SqlUpdate("INSERT INTO composite_question (question_id, allow_multiple, add_button_template_id, "
            + "additional_item_template_id, unwrap_on_export, child_orientation_type_id)"
            + " VALUES(:questionId, :allowMultiple, :addButtonTemplateId, :additionalItemTemplateId, :unwrapOnExport,"
            + "(select orientation_type_id from orientation_type where orientation_type_code = :childOrientation))")
    int insertParent(@Bind("questionId") long compositeQuestionId, @Bind("allowMultiple") boolean allowMultiple,
                     @Bind("addButtonTemplateId") Long addButtonTemplateId, @Bind("additionalItemTemplateId") Long itemTemplateId,
                     @Bind("childOrientation") OrientationType childOrientation, @Bind("unwrapOnExport") boolean unwrapOnExport);

    default void insertChildren(long compositeQuestionId, List<Long> childQuestionIds) {
        insertChild(compositeQuestionId, childQuestionIds, IntStream.rangeClosed(0, childQuestionIds.size() - 1).boxed().collect(toList()));
    }

    @SqlBatch("INSERT INTO composite_question__question (parent_question_id, child_question_id, display_order)"
            + " VALUES(:parentQuestionId, :childQuestionId, :orderIndex)")
    void insertChild(@Bind("parentQuestionId") long parentQuestionId, @Bind("childQuestionId") List<Long> childQuestionIds,
                     @Bind("orderIndex") List<Integer> orderIdxs);

}
