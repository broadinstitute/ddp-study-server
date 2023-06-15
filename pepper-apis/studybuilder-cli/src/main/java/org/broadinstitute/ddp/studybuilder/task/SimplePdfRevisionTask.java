package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.PdfBuilder;
import org.jdbi.v3.core.Handle;
import java.util.List;

public class SimplePdfRevisionTask extends SimpleRevisionTask {
    @Override
    public void run(Handle handle) {
        StudyDto studyDto = getStudyDto(handle);
        User adminUser = getAdminUser(handle);

        List<? extends Config> pdfs = getConfigList(dataCfg, "pdfs", () -> {
            throw new DDPException("Missing pdfs config list.");
        });

        //insert new version
        PdfBuilder pdfBuilder = new PdfBuilder(cfgPath.getParent(), studyCfg, studyDto, adminUser.getId());
        pdfs.forEach(pdf -> pdfBuilder.insertPdfConfig(handle, pdf));

    }
}
