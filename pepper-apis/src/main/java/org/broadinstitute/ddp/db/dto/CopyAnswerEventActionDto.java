package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.event.CopyAnswerTarget;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class CopyAnswerEventActionDto extends EventActionDto {
    private String copySourceStableCode;
    private CopyAnswerTarget copyAnswerTarget;

    @JdbiConstructor
    public CopyAnswerEventActionDto(@ColumnName("id") long id, @ColumnName("messageDestinationId") Long messageDestinationId,
                                    @ColumnName("stableCode")String copySourceStableCode,
                                    @ColumnName("copyTarget")CopyAnswerTarget copyAnswerTarget) {
        super(id, messageDestinationId);
        this.copySourceStableCode = copySourceStableCode;
        this.copyAnswerTarget = copyAnswerTarget;
    }

    public String getCopySourceStableCode() {
        return copySourceStableCode;
    }

    public CopyAnswerTarget getCopyAnswerTarget() {
        return copyAnswerTarget;
    }
}
