package org.broadinstitute.ddp.model.migration;

import java.util.List;

import org.broadinstitute.ddp.model.activity.types.QuestionType;

public class DependentQuestionDto {

    String question;
    List<String> options;
    QuestionType questionType;

    public DependentQuestionDto(String question, List<String> options, QuestionType questionType) {
        this.question = question;
        this.options = options;
        this.questionType = questionType;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }
}
