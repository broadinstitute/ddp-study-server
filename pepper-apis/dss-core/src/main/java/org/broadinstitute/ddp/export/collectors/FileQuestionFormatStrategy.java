package org.broadinstitute.ddp.export.collectors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.FileInfo;

/**
 * Rule:
 * - single column for question response
 * - value formatted as text (the file upload guid)
 * - null value (or missing file upload) results in empty cell
 */
public class FileQuestionFormatStrategy implements ResponseFormatStrategy<FileQuestionDef, FileAnswer> {

    @Override
    public Map<String, Object> mappings(FileQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put(definition.getStableId(), MappingUtil.newTextType());
        return props;
    }

    @Override
    public Map<String, Object> questionDef(FileQuestionDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put("stableId", definition.getStableId());
        props.put("questionType", definition.getQuestionType().name());
        props.put("questionText", HtmlConverter.getPlainText(definition.getPromptTemplate().renderWithDefaultValues("en")));
        return props;
    }

    @Override
    public List<String> headers(FileQuestionDef definition) {
        return List.of(definition.getStableId());
    }

    @Override
    public Map<String, String> collect(FileQuestionDef question, FileAnswer answer) {
        Map<String, String> record = new HashMap<>();
        String uploadGuid = answer.getValue() != null
                ? answer.getValue().stream().map(FileInfo::getUploadGuid).collect(Collectors.joining(",")) : "";
        record.put(question.getStableId(), uploadGuid);
        return record;
    }
}
