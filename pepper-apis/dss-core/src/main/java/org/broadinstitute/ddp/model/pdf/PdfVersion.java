package org.broadinstitute.ddp.model.pdf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Version info about a pdf configuration, including the data sources needed by this version of the pdf.
 */
public class PdfVersion {

    private long id;
    private long configId;
    private String versionTag;
    private long revId;
    private long revStart;
    private Long revEnd;

    private List<PdfDataSource> sources = new ArrayList<>();

    @JdbiConstructor
    public PdfVersion(@ColumnName("pdf_document_version_id") long id,
                      @ColumnName("pdf_document_configuration_id") long configId,
                      @ColumnName("version_tag") String versionTag,
                      @ColumnName("revision_id") long revId,
                      @ColumnName("revision_start") long revStart,
                      @ColumnName("revision_end") Long revEnd) {
        this.id = id;
        this.configId = configId;
        this.versionTag = versionTag;
        this.revId = revId;
        this.revStart = revStart;
        this.revEnd = revEnd;
    }

    public PdfVersion(String versionTag, long revId) {
        this.versionTag = versionTag;
        this.revId = revId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getConfigId() {
        return configId;
    }

    public void setConfigId(long configId) {
        this.configId = configId;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public long getRevId() {
        return revId;
    }

    public long getRevStart() {
        return revStart;
    }

    public Long getRevEnd() {
        return revEnd;
    }

    public List<PdfDataSource> getDataSources() {
        return sources;
    }

    public void addDataSource(PdfDataSource source) {
        sources.add(source);
    }

    public boolean hasDataSource(PdfDataSourceType type) {
        return sources.stream().anyMatch(src -> src.getType() == type);
    }

    public Map<String, Set<String>> getAcceptedActivityVersions() {
        Map<String, Set<String>> accepted = new HashMap<>();
        for (PdfDataSource source : sources) {
            if (source.getType() == PdfDataSourceType.ACTIVITY) {
                PdfActivityDataSource src = (PdfActivityDataSource) source;
                accepted.computeIfAbsent(src.getActivityCode(), key -> new HashSet<>()).add(src.getVersionTag());
            }
        }
        return accepted;
    }
}
