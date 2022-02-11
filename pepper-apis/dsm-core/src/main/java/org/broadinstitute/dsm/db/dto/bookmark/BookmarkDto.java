package org.broadinstitute.dsm.db.dto.bookmark;

import java.util.Optional;

import lombok.Setter;

@Setter
public class BookmarkDto {

    private int bookmarkId;
    private long value;
    private String instance;

    public int getBookmarkId() {
        return bookmarkId;
    }

    public long getValue() {
        return value;
    }

    public Optional<String> getInstance() {
        return Optional.ofNullable(instance);
    }

    private BookmarkDto(Builder builder) {
        this.bookmarkId = builder.bookmarkId;
        this.value = builder.value;
        this.instance = builder.instance;
    }

    public static class Builder {

        private int bookmarkId;
        private long value;
        private String instance;

        public Builder(long value, String instance) {
            this.value = value;
            this.instance = instance;
        }

        public Builder withBookmarkId(int bookmarkId) {
            this.bookmarkId = bookmarkId;
            return this;
        }

        public BookmarkDto build() {
            return new BookmarkDto(this);
        }


    }
}
