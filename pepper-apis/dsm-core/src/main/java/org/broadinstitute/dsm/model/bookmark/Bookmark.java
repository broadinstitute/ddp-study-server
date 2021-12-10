package org.broadinstitute.dsm.model.bookmark;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.bookmark.BookmarkDao;
import org.broadinstitute.dsm.db.dto.bookmark.BookmarkDto;

public class Bookmark {

    private static final String FAMILY_ID_SUFFIX = "_family_id";
    private BookmarkDao bookmarkDao;

    public Bookmark() {
        this.bookmarkDao = new BookmarkDao();
    }

    public long getBookmarkFamilyIdAndUpdate(String realm) {
        if (StringUtils.isBlank(realm)) throw new IllegalArgumentException("realm should not be blank");
        final String familyIdInstance = realm + FAMILY_ID_SUFFIX;
        Optional<BookmarkDto> maybeBookmarkByInstance = bookmarkDao.getBookmarkByInstance(familyIdInstance);
        return maybeBookmarkByInstance
                .map(bookmarkDto -> {
                    bookmarkDao.updateBookmarkValueByBookmarkId(bookmarkDto.getBookmarkId(), bookmarkDto.getValue() + 1);
                    return bookmarkDto.getValue();
                })
                .orElseThrow();
    }


}
