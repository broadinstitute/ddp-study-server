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
    private List<PdfTemplate> templates;

    public PdfConfiguration(PdfConfigInfo info, PdfVersion version, List<PdfTemplate> templates) {
        this.info = info;
        this.version = version;
        this.templates = templates;
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

    public List<PdfTemplate> getTemplates() {
        return templates;
    }

    public void addTemplate(PdfTemplate template) {
        templates.add(template);
    }
}
