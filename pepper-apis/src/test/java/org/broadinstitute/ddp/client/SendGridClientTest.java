package org.broadinstitute.ddp.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import com.google.gson.Gson;
import com.sendgrid.Content;
import com.sendgrid.Email;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class SendGridClientTest {

    private static Gson gson = new Gson();

    private SendGrid mockSG;
    private SendGridClient sendGrid;

    @Before
    public void setup() {
        mockSG = mock(SendGrid.class);
        sendGrid = new SendGridClient(mockSG);
    }

    @Test
    public void testGetTemplateActiveVersionId() throws IOException {
        SendGridClient.Template expected = new SendGridClient.Template(
                "abc", "a template", List.of(new SendGridClient.TemplateVersion("v1", 1)));

        when(mockSG.api(any(Request.class)))
                .thenReturn(new Response(200, gson.toJson(expected), null));
        var result = sendGrid.getTemplateActiveVersionId("abc");

        assertNotNull(result);
        assertNull(result.getError());
        assertNull(result.getThrown());
        assertEquals(200, result.getStatusCode());
        assertEquals("v1", result.getBody());

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockSG, times(1)).api(captor.capture());

        Request actualRequest = captor.getValue();
        assertEquals(Method.GET, actualRequest.getMethod());
        assertTrue(actualRequest.getEndpoint().contains("abc"));
    }

    @Test
    public void testGetTemplateActiveVersionId_noActive() throws IOException {
        SendGridClient.Template expected = new SendGridClient.Template(
                "abc", "a template", List.of(new SendGridClient.TemplateVersion("v1", 0)));

        when(mockSG.api(any(Request.class)))
                .thenReturn(new Response(200, gson.toJson(expected), null));
        var result = sendGrid.getTemplateActiveVersionId("abc");

        assertEquals(200, result.getStatusCode());
        assertNull(result.getBody());
    }

    @Test
    public void testGetTemplateActiveVersionId_error() throws IOException {
        when(mockSG.api(any(Request.class)))
                .thenReturn(new Response(400, null, null));
        var result = sendGrid.getTemplateActiveVersionId("abc");

        assertNull(result.getBody());
        assertNull(result.getError());
        assertNull(result.getThrown());
        assertEquals(400, result.getStatusCode());
    }

    @Test
    public void testGetTemplateActiveVersionId_exception() throws IOException {
        when(mockSG.api(any(Request.class)))
                .thenThrow(new IOException("from test"));
        var result = sendGrid.getTemplateActiveVersionId("abc");

        assertNull(result.getBody());
        assertNull(result.getError());
        assertNotNull(result.getThrown());
        assertEquals(500, result.getStatusCode());
        assertEquals("from test", result.getThrown().getMessage());
    }

    @Test
    public void testSendMail() throws IOException {
        when(mockSG.api(any(Request.class)))
                .thenReturn(new Response(200, null, null));

        var mail = new Mail(
                new Email("from@ddp.org", "pepper"),
                "subject foo",
                new Email("to@ddp.org", "bar"),
                new Content("text/plain", "test email"));
        var result = sendGrid.sendMail(mail);

        assertNotNull(result);
        assertNull(result.getBody());
        assertNull(result.getError());
        assertNull(result.getThrown());
        assertEquals(200, result.getStatusCode());

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockSG, times(1)).api(captor.capture());

        Request actualRequest = captor.getValue();
        assertEquals(Method.POST, actualRequest.getMethod());
        assertEquals(SendGridClient.PATH_MAIL_SEND, actualRequest.getEndpoint());

        String payload = actualRequest.getBody();
        assertTrue(payload.contains("from@ddp.org"));
        assertTrue(payload.contains("pepper"));
        assertTrue(payload.contains("to@ddp.org"));
        assertTrue(payload.contains("bar"));
        assertTrue(payload.contains("subject foo"));
        assertTrue(payload.contains("text/plain"));
        assertTrue(payload.contains("test email"));
    }

    @Test
    public void testSendMail_success() throws IOException {
        when(mockSG.api(any(Request.class)))
                .thenReturn(new Response(200, "from test", null));
        var result = sendGrid.sendMail(new Mail());
        assertNull(result.getError());
        assertEquals(200, result.getStatusCode());

        reset(mockSG);
        when(mockSG.api(any(Request.class)))
                .thenReturn(new Response(202, "from test", null));
        result = sendGrid.sendMail(new Mail());
        assertNull(result.getError());
        assertEquals(202, result.getStatusCode());
    }

    @Test
    public void testSendMail_error() throws IOException {
        when(mockSG.api(any(Request.class)))
                .thenReturn(new Response(400, "from test", null));
        var result = sendGrid.sendMail(new Mail());

        assertNull(result.getBody());
        assertNull(result.getThrown());
        assertEquals(400, result.getStatusCode());
        assertEquals("from test", result.getError());
    }

    @Test
    public void testSendMail_exception() throws IOException {
        when(mockSG.api(any(Request.class)))
                .thenThrow(new IOException("from test"));
        var result = sendGrid.sendMail(new Mail());

        assertNull(result.getBody());
        assertNull(result.getError());
        assertNotNull(result.getThrown());
        assertEquals(500, result.getStatusCode());
        assertEquals("from test", result.getThrown().getMessage());
    }
}
