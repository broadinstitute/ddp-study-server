package org.broadinstitute.ddp.model.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents all the configurations needed to generate a pdf document. Encapsulates metadata, version info, pdf file templates, and field
 * substitutions.
 */
public class PdfConfiguration {

    private PdfConfigInfo info;
    private PdfVersion version;
    private List<Long> templateIds;

    public PdfConfiguration(PdfConfigInfo info, PdfVersion version, List<Long> templateIds) {
        this.info = info;
        this.version = version;
        this.templateIds = templateIds;
    }

    public PdfConfiguration(PdfConfigInfo info, PdfVersion version) {
        this(info, version, new ArrayList<>());
    }

    public long getId() {
        return info.getId();
    }

    public long getStudyId() {
        return info.getStudyId();
    }

    public String getStudyGuid() {
        return info.getStudyGuid();
    }

    public String getConfigName() {
        return info.getConfigName();
    }

    public String getFilename() {
        return info.getFilename();
    }

    public String getDisplayName() {
        return info.getDisplayName();
    }

    public PdfConfigInfo getInfo() {
        return info;
    }

    public PdfVersion getVersion() {
        return version;
    }

    public List<Long> getTemplateIds() {
        return templateIds;
    }

    public void addTemplateId(Long templateId) {
        templateIds.add(templateId);
    }

}
