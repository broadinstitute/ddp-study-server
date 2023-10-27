package org.broadinstitute.ddp.studybuilder.task.pancan;

import com.typesafe.config.Config;
import org.apache.commons.cli.ParseException;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.studybuilder.task.SimplePdfRevisionTask;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;

public class PancanPdfVersion2 implements CustomTask {

    SimplePdfRevisionTask pdfRevisionTask = new SimplePdfRevisionTask();

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        pdfRevisionTask.init(cfgPath, studyCfg, varsCfg);

        try {
            pdfRevisionTask.consumeArguments(
                    new String[]{"patches/study-pdfs-v2.conf"});
        } catch (ParseException parseException) {
            throw new DDPException(parseException.getMessage());
        }
    }

    @Override
    public void run(Handle handle) {
        pdfRevisionTask.run(handle);
    }
}
