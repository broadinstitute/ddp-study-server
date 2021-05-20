package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;


public final class FileQuestionDto extends QuestionDto implements Serializable {

    private long maxFileSize;
    private List<String> mimeTypes = new ArrayList<>();

    @JdbiConstructor
    public FileQuestionDto(@Nested QuestionDto questionDto,
                           @ColumnName("max_file_size") long maxFileSize,
                           @ColumnName("mime_types") String mimeTypes) {
        super(questionDto);
        this.maxFileSize = maxFileSize;
        if (StringUtils.isNotBlank(mimeTypes)) {
            this.mimeTypes = Arrays.stream(mimeTypes.split(",")).collect(Collectors.toList());
        }
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public List<String> getMimeTypes() {
        return mimeTypes;
    }
}
