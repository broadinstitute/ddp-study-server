package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CompositeAnswerSummaryDto {
    private long id;
    private String guid;
    private String questionStableId;
    private long activityInstanceId;
    private final List<List<AnswerDto>> childAnswers = new ArrayList<>();

    public List<AnswerDto> getLastRowOfChildrenAnswers() {
        return childAnswers.get(childAnswers.size() - 1);
    }
}
