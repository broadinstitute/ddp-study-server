package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.interfaces.FileUploadSettings;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;


@Getter(onMethod = @__(@Override))
public final class FileQuestionDto extends QuestionDto implements Serializable, FileUploadSettings {
    private final long maxFileSize;
    private final Set<String> mimeTypes;

    @JdbiConstructor
    public FileQuestionDto(@Nested QuestionDto questionDto,
                           @ColumnName("max_file_size") long maxFileSize,
                           @ColumnName("mime_types") String mimeTypes) {
        super(questionDto);

        this.maxFileSize = maxFileSize;
        this.mimeTypes = Optional.ofNullable(mimeTypes)
                .filter(StringUtils::isNotBlank)
                .map(types -> types.split(","))
                .map(Arrays::stream)
                .map(x -> x.collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElse(null);
    }
}
