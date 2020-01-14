package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.event.CopyLocationType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class CopyAnswerEventActionDto extends EventActionDto {
    private String copySourceStableCode;
    private CopyLocationType copyAnswerTarget;

    @JdbiConstructor
    public CopyAnswerEventActionDto(@ColumnName("id") long id, @ColumnName("messageDestinationId") Long messageDestinationId,
                                    @ColumnName("stableCode")String copySourceStableCode,
                                    @ColumnName("copyTarget") CopyLocationType copyAnswerTarget) {
        super(id, messageDestinationId);
        this.copySourceStableCode = copySourceStableCode;
        this.copyAnswerTarget = copyAnswerTarget;
    }

    public String getCopySourceStableCode() {
        return copySourceStableCode;
    }

    public CopyLocationType getCopyAnswerTarget() {
        return copyAnswerTarget;
    }
}
