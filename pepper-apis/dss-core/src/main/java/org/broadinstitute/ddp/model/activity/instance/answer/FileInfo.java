package org.broadinstitute.ddp.model.activity.instance.answer;

import java.io.Serializable;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class FileInfo implements Serializable {
    @ColumnName("file_upload_id")
    transient long uploadId;

    @ColumnName("file_upload_guid")
    @SerializedName("uploadGuid")
    String uploadGuid;

    @NotBlank
    @ColumnName("file_name")
    @SerializedName("fileName")
    String fileName;

    @Positive
    @ColumnName("file_size")
    @SerializedName("fileSize")
    long fileSize;
}
