package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.interfaces.FileUploadSettings;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;


public final class FileQuestionDto extends QuestionDto implements Serializable, FileUploadSettings {

    private long maxFileSize;
    private Set<String> mimeTypes = new LinkedHashSet<>();

    @JdbiConstructor
    public FileQuestionDto(@Nested QuestionDto questionDto,
                           @ColumnName("max_file_size") long maxFileSize,
                           @ColumnName("mime_types") String mimeTypes) {
        super(questionDto);
        this.maxFileSize = maxFileSize;
        if (StringUtils.isNotBlank(mimeTypes)) {
            this.mimeTypes =  new LinkedHashSet(Arrays.stream(mimeTypes.split(",")).collect(Collectors.toList()));
        }
    }

    @Override
    public long getMaxFileSize() {
        return maxFileSize;
    }

    @Override
    public Set<String> getMimeTypes() {
        return mimeTypes;
    }
}
