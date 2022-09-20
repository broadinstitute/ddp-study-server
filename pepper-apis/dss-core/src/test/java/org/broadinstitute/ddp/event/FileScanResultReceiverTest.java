package org.broadinstitute.ddp.event;

import static org.broadinstitute.ddp.event.FileScanResultReceiver.ATTR_BUCKET_ID;
import static org.broadinstitute.ddp.event.FileScanResultReceiver.ATTR_OBJECT_ID;
import static org.broadinstitute.ddp.event.FileScanResultReceiver.ATTR_SCAN_RESULT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.storage.Blob;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.model.files.FileScanResult;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.junit.Test;

public class FileScanResultReceiverTest {

    @Test
    public void testReceiveMessage() {
        // Create mocks.
        var mockReply = mock(AckReplyConsumer.class);
        var mockStorage = mock(GoogleBucketClient.class);
        var mockHandle = mock(Handle.class);
        var mockFileDao = mock(FileUploadDao.class);
        var mockExportDao = mock(DataExportDao.class);
        var mockBlob = mock(Blob.class);
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var upload = FileUpload.builder()
                .id(1L)
                .guid("guid")
                .studyId(1L)
                .operatorUserId(1L)
                .participantUserId(1L)
                .blobName("guid_filename")
                .mimeType("mime")
                .fileName("name")
                .fileSize(123L)
                .isVerified(true)
                .createdAt(now)
                .build();
        var msg = PubsubMessage.newBuilder()
                .setMessageId("foo")
                .putAttributes(ATTR_BUCKET_ID, "uploads")
                .putAttributes(ATTR_OBJECT_ID, "foo/bar/guid_filename")
                .putAttributes(ATTR_SCAN_RESULT, FileScanResult.CLEAN.name())
                .build();

        // Setup behavior.
        doReturn(true).when(mockBlob).exists();
        doReturn("foo/bar/guid_filename").when(mockBlob).getName();
        doReturn(now.toEpochMilli()).when(mockBlob).getCreateTime();
        doReturn(mockBlob).when(mockStorage).getBlob(any(), any());
        doReturn(mockFileDao).when(mockHandle).attach(FileUploadDao.class);
        doReturn(Optional.of(upload)).when(mockFileDao).findAndLockByGuid(any());
        doReturn(mockExportDao).when(mockHandle).attach(DataExportDao.class);

        var receiverSpy = spy(new FileScanResultReceiver(mockStorage, "uploads", "scanned", "quarantine"));
        doAnswer(invocation -> ((HandleCallback) invocation.getArgument(0)).withHandle(mockHandle))
                .when(receiverSpy).withAPIsTxn(any());

        // Test it!
        receiverSpy.receiveMessage(msg, mockReply);

        // Do asserts.
        verify(mockStorage).getBlob("uploads", "foo/bar/guid_filename");
        verify(mockStorage).moveBlob(any(), eq("scanned"), eq("foo/bar/guid_filename"));
        verify(mockFileDao).findAndLockByGuid("guid");
        verify(mockExportDao, times(1)).queueDataSync(1L, 1L);
        verify(mockReply, times(1)).ack();
        verify(mockReply, never()).nack();
    }

    @Test
    public void testReceiveMessage_checksPayload_andGracefullyAcks() {
        var builder = PubsubMessage.newBuilder().setMessageId("foo");
        var replyMock = mock(AckReplyConsumer.class);
        var receiver = new FileScanResultReceiver(null, "uploads", "scanned", "quarantine");

        builder.clearAttributes();
        receiver.receiveMessage(builder.build(), replyMock);
        verify(replyMock, times(1)).ack();
        reset(replyMock);

        builder.putAttributes(ATTR_BUCKET_ID, "not-uploads");
        receiver.receiveMessage(builder.build(), replyMock);
        verify(replyMock, times(1)).ack();
        reset(replyMock);

        builder.putAttributes(ATTR_BUCKET_ID, "uploads");
        builder.putAttributes(ATTR_OBJECT_ID, "foo.pdf");
        builder.putAttributes(ATTR_SCAN_RESULT, "unknown-scan-result");
        receiver.receiveMessage(builder.build(), replyMock);
        verify(replyMock, times(1)).ack();
        reset(replyMock);
    }
}
