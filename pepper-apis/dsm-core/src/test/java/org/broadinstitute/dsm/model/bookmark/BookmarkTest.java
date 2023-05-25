package org.broadinstitute.dsm.model.bookmark;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;
import org.junit.Assert;
import org.junit.Test;

public class BookmarkTest {

    @Test
    public void getThenIncrementBookmarkValue() {
        BookmarkDao bookmarkDao = mock(BookmarkDao.class);
        String instanceId = "rgp_family_id";
        long value = 1000;
        int bookmarkId = 1;
        BookmarkDto bookmarkDto = new BookmarkDto.Builder(value, instanceId).withBookmarkId(bookmarkId).build();

        when(bookmarkDao.getBookmarkByInstance(instanceId)).thenReturn(Optional.of(bookmarkDto));
        when(bookmarkDao.updateBookmarkValueByBookmarkId(bookmarkId, value)).thenReturn(1);
        Bookmark bookmark = new Bookmark(bookmarkDao);
        Assert.assertEquals(value, bookmark.getThenIncrementBookmarkValue(instanceId));
        verify(bookmarkDao).updateBookmarkValueByBookmarkId(bookmarkId, value + 1);
    }
}
