package org.broadinstitute.ddp.service;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.jdbi.v3.core.Handle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PdfServiceTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testFindConfigVersionForUser_noPdfConfigVersions_errors() {
        thrown.expect(DaoException.class);
        thrown.expectMessage(containsString("No versions found"));
        thrown.expectMessage(containsString("pdf config with id=123456789"));

        PdfDao dao = mock(PdfDao.class);
        when(dao.findOrderedConfigVersionsByConfigId(123456789L)).thenReturn(new ArrayList<>());
        Handle handle = mock(Handle.class);
        when(handle.attach(PdfDao.class)).thenReturn(dao);

        new PdfService().findConfigVersionForUser(handle, 123456789L, "user", "study");
    }

    @Test
    public void testFindConfigVersionForUser_returnsLatestVersionWithNoActivitySource() {
        PdfVersion expected = new PdfVersion("latest", 1L);
        PdfVersion other = new PdfVersion("not-this-one", 2L);

        PdfDao dao = mock(PdfDao.class);
        when(dao.findOrderedConfigVersionsByConfigId(1L)).thenReturn(Arrays.asList(expected, other));
        Handle handle = mock(Handle.class);
        when(handle.attach(PdfDao.class)).thenReturn(dao);

        PdfVersion actual = new PdfService().findConfigVersionForUser(handle, 1L, "user", "study");
        assertNotNull(actual);
        assertEquals(actual.getVersionTag(), expected.getVersionTag());
        assertEquals(actual.getRevId(), expected.getRevId());
    }

    @Test
    public void testFindConfigVersionForUser_returnsFirstMatchingVersion() {
        PdfVersion expected = new PdfVersion("expected", 1L);
        expected.addDataSource(new PdfActivityDataSource(1L, 1L, "act1", 1L, "tag1"));
        PdfVersion fallback = new PdfVersion("fallback", 2L);

        ActivityResponse instance = new FormResponse(1L, "guid", 1L, false, 1L, 1L, 1L, "act1", "tag1", null);

        PdfDao pdfDao = mock(PdfDao.class);
        when(pdfDao.findOrderedConfigVersionsByConfigId(1L)).thenReturn(Arrays.asList(expected, fallback));
        ActivityInstanceDao instanceDao = mock(ActivityInstanceDao.class);
        when(instanceDao.findBaseResponsesByStudyAndUserGuid("study", "user")).thenReturn(Arrays.asList(instance));
        Handle handle = mock(Handle.class);
        when(handle.attach(PdfDao.class)).thenReturn(pdfDao);
        when(handle.attach(ActivityInstanceDao.class)).thenReturn(instanceDao);

        PdfVersion actual = new PdfService().findConfigVersionForUser(handle, 1L, "user", "study");
        assertNotNull(actual);
        assertEquals(expected.getVersionTag(), actual.getVersionTag());
        assertEquals(expected.getRevId(), actual.getRevId());
    }

    @Test
    public void testFindConfigVersionForUser_matchesAllNeededActivities() {
        PdfVersion expected = new PdfVersion("expected", 1L);
        expected.addDataSource(new PdfActivityDataSource(1L, 1L, "act1", 1L, "tag1"));
        expected.addDataSource(new PdfActivityDataSource(2L, 2L, "act2", 2L, "tag2"));
        PdfVersion fallback = new PdfVersion("fallback", 2L);

        ActivityResponse instance1 = new FormResponse(1L, "guid1", 1L, false, 1L, 1L, 1L, "act1", "tag1", null);
        ActivityResponse instance2 = new FormResponse(2L, "guid2", 1L, false, 2L, 2L, 2L, "act2", "tag2", null);

        PdfDao pdfDao = mock(PdfDao.class);
        when(pdfDao.findOrderedConfigVersionsByConfigId(1L)).thenReturn(Arrays.asList(expected, fallback));
        ActivityInstanceDao instanceDao = mock(ActivityInstanceDao.class);
        when(instanceDao.findBaseResponsesByStudyAndUserGuid("study", "user")).thenReturn(Arrays.asList(instance1, instance2));
        Handle handle = mock(Handle.class);
        when(handle.attach(PdfDao.class)).thenReturn(pdfDao);
        when(handle.attach(ActivityInstanceDao.class)).thenReturn(instanceDao);

        PdfVersion actual = new PdfService().findConfigVersionForUser(handle, 1L, "user", "study");
        assertNotNull(actual);
        assertEquals(expected.getVersionTag(), actual.getVersionTag());
        assertEquals(expected.getRevId(), actual.getRevId());
    }

    @Test
    public void testFindConfigVersionForUser_matchesVersionThatAllowsMultipleVersionsOfActivity() {
        PdfVersion expected = new PdfVersion("expected", 1L);
        expected.addDataSource(new PdfActivityDataSource(1L, 1L, "act1", 1L, "tag1"));
        expected.addDataSource(new PdfActivityDataSource(2L, 1L, "act1", 2L, "tag2"));
        PdfVersion fallback = new PdfVersion("fallback", 2L);

        ActivityResponse instance = new FormResponse(1L, "guid", 1L, false, 1L, 1L, 1L, "act1", "tag2", null);

        PdfDao pdfDao = mock(PdfDao.class);
        when(pdfDao.findOrderedConfigVersionsByConfigId(1L)).thenReturn(Arrays.asList(expected, fallback));
        ActivityInstanceDao instanceDao = mock(ActivityInstanceDao.class);
        when(instanceDao.findBaseResponsesByStudyAndUserGuid("study", "user")).thenReturn(Arrays.asList(instance));
        Handle handle = mock(Handle.class);
        when(handle.attach(PdfDao.class)).thenReturn(pdfDao);
        when(handle.attach(ActivityInstanceDao.class)).thenReturn(instanceDao);

        PdfVersion actual = new PdfService().findConfigVersionForUser(handle, 1L, "user", "study");
        assertNotNull(actual);
        assertEquals(expected.getVersionTag(), actual.getVersionTag());
        assertEquals(expected.getRevId(), actual.getRevId());
    }

    @Test
    public void testFindConfigVersionForUser_whenUserDoesNotMatchAnyVersions_returnsLatestVersion() {
        PdfVersion expected = new PdfVersion("expected", 1L);
        expected.addDataSource(new PdfActivityDataSource(1L, 1L, "act1", 1L, "tag1"));
        PdfVersion other = new PdfVersion("other", 2L);
        other.addDataSource(new PdfActivityDataSource(2L, 2L, "act2", 2L, "tag2"));

        ActivityResponse instance = new FormResponse(1L, "guid", 1L, false, 1L, 1L, 3L, "act3", "tag3", null);

        PdfDao pdfDao = mock(PdfDao.class);
        when(pdfDao.findOrderedConfigVersionsByConfigId(1L)).thenReturn(Arrays.asList(expected, other));
        ActivityInstanceDao instanceDao = mock(ActivityInstanceDao.class);
        when(instanceDao.findBaseResponsesByStudyAndUserGuid("study", "user")).thenReturn(Arrays.asList(instance));
        Handle handle = mock(Handle.class);
        when(handle.attach(PdfDao.class)).thenReturn(pdfDao);
        when(handle.attach(ActivityInstanceDao.class)).thenReturn(instanceDao);

        PdfVersion actual = new PdfService().findConfigVersionForUser(handle, 1L, "user", "study");
        assertNotNull(actual);
        assertEquals(expected.getVersionTag(), actual.getVersionTag());
        assertEquals(expected.getRevId(), actual.getRevId());
    }
}
