package org.broadinstitute.dsm.util;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.GoogleBucket;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class JavaHeapDumper {

    private static final Logger logger = LoggerFactory.getLogger(JavaHeapDumper.class);

    public static final String DEFAULT_LOCAL_PATH = "/tmp";
    public static final String DEFAULT_BUCKET_PATH = "heap_dumps";

    public void dumpHeapToLocalFile(String filePath) throws IOException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
                server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
        logger.info("About to create local file at:" + filePath);
        mxBean.dumpHeap(filePath, true);
    }

    public void dumpHeapToBucket(String bucketName) throws IOException {
        dumpHeapToBucket(bucketName, generateDumpFileName());
    }

    public void dumpHeapToBucket(String bucketName, final String fileName) throws IOException {
        String gcpName = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME);
        dumpHeapToLocalFile(DEFAULT_LOCAL_PATH + "/" + fileName);
        File localDumpFile = Paths.get(DEFAULT_LOCAL_PATH, fileName).toFile();
        if (localDumpFile.exists()) {
            logger.info("Created local dump file: " + localDumpFile.getAbsolutePath() + " with size: " + localDumpFile.length());
        } else {
            throw new RuntimeException("Could not locate local dump file"  + DEFAULT_LOCAL_PATH + "/" + fileName);
        }
        try (FileInputStream localDumpFileStream = new FileInputStream(localDumpFile)) {
            String credentials = null;
            if (TransactionWrapper.hasConfigPath(ApplicationConfigConstants.GOOGLE_CREDENTIALS)) {
                String tmp = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_CREDENTIALS);
                if (StringUtils.isNotBlank(tmp) && new File(tmp).exists()) {
                    credentials = tmp;
                }
            }
           GoogleBucket.uploadFile(credentials, gcpName, bucketName, DEFAULT_BUCKET_PATH + "/" + fileName,
                    localDumpFileStream);

            logger.info("Heap dump saved to bucket:" + bucketName + " to path: " + DEFAULT_BUCKET_PATH + "/" + fileName);
        } finally {
            if (localDumpFile.exists()) {
                localDumpFile.delete();
            }
        }
    }

    private String generateDumpFileName() {
        String gcpFileName = generateGcpFileName();
        return gcpFileName != null ? gcpFileName : nonGcpFileName();
    }

    private String generateGcpFileName() {
        String appName = System.getenv("GAE_APPLICATION");
        String serviceName = System.getenv("GAE_SERVICE");
        String instanceId = System.getenv("GAE_INSTANCE");
        String dateTime = DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now());
        return appName != null ? appName + "-" + serviceName + "-" + instanceId + "-" + dateTime + ".hprof" : null;
    }

    private String nonGcpFileName() {
        return "heapDump-" + DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()) + ".hprof";
    }
}
