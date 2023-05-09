package org.broadinstitute.dsm.model.bookmark;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;

public class Bookmark {

    private static final String FAMILY_ID_SUFFIX = "_family_id";
    private final BookmarkDao bookmarkDao;

    public Bookmark() {
        this.bookmarkDao = new BookmarkDao();
    }

    public Bookmark(BookmarkDao bookmarkDao) {
        this.bookmarkDao = bookmarkDao;
    }

    public long getBookmarkFamilyIdAndUpdate(String realm) {
        if (StringUtils.isBlank(realm)) {
            throw new IllegalArgumentException("realm should not be blank");
        }
        return getThenIncrementBookmarkValue(realm + FAMILY_ID_SUFFIX);
    }

    /**
     * Return bookmark DTO corresponding to row for bookmark ID
     */
    public BookmarkDto getBookmarkDto(String bookmarkId) {
        Optional<BookmarkDto> bookmarkDto = bookmarkDao.getBookmarkByInstance(bookmarkId);
        if (bookmarkDto.isEmpty()) {
            throw new RuntimeException("No bookmark found for bookmark ID " + bookmarkId);
        }
        return bookmarkDto.get();
    }

    /**
     * Get current bookmark value then increment bookmark value by 1
     * @param instance ID for bookmark
     * @return bookmark value before increment
     */
    public long getThenIncrementBookmarkValue(String instanceId) {
        BookmarkDto bookmarkDto = getBookmarkDto(instanceId);
        long value = bookmarkDto.getValue();
        bookmarkDao.updateBookmarkValueByBookmarkId(bookmarkDto.getBookmarkId(), value + 1);
        return value;
    }
}
