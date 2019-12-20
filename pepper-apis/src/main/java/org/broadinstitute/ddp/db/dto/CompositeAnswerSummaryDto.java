package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;

public class CompositeAnswerSummaryDto {

    private long id;
    private String guid;
    private String questionStableId;
    private List<List<ChildAnswerDto>> childAnswers;

    public CompositeAnswerSummaryDto() {
        this.childAnswers = new ArrayList<>();
    }

    public List<ChildAnswerDto> getLastRowOfChildrenAnswers() {
        if (childAnswers == null) {
            return null;
        } else {
            return childAnswers.get(childAnswers.size() - 1);
        }

    }

    public List<List<ChildAnswerDto>> getChildAnswers() {
        return childAnswers;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public void setQuestionStableId(String questionStableId) {
        this.questionStableId = questionStableId;
    }
}
