package com.nodeadmin.common.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Immutable pagination envelope returned by service list methods.
 *
 * <p>Mirrors NodeAdmin's {@code PaginateResult} helper (src/helpers/functions.ts).
 * Use the helper methods to build page-navigation metadata for API responses.
 *
 * <p>JSON shape (matches NodeAdmin standard):
 * <pre>
 * {
 *   "datas": [...],
 *   "paginate_data": {
 *     "total_data": N,
 *     "page_size":  N,
 *     "current_page": N,
 *     "total_page": N
 *   }
 * }
 * </pre>
 *
 * @param <T>       element type
 * @param data      current-page records  (serialised as {@code "datas"})
 * @param total     total record count across all pages
 * @param page      current 1-based page number
 * @param pageSize  number of records per page
 * @param totalPages total number of pages
 */
public record PaginateResult<T>(
        @JsonProperty("datas") List<T> data,
        @JsonIgnore            long    total,
        @JsonIgnore            int     page,
        @JsonIgnore            int     pageSize,
        @JsonIgnore            int     totalPages
) {

    /**
     * Convenience factory — computes {@code totalPages} automatically.
     */
    public static <T> PaginateResult<T> of(List<T> data, long total, int page, int pageSize) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        return new PaginateResult<>(data, total, page, pageSize, totalPages);
    }

    /**
     * Nested pagination metadata — serialised as {@code "paginate_data"}.
     *
     * @param totalData    total record count
     * @param pageSize     records per page
     * @param currentPage  current 1-based page number
     * @param totalPage    total number of pages
     */
    public record PaginateData(
            @JsonProperty("total_data")    long totalData,
            @JsonProperty("page_size")     int  pageSize,
            @JsonProperty("current_page")  int  currentPage,
            @JsonProperty("total_page")    int  totalPage
    ) {}

    /** Returns the nested paginate_data object for JSON serialisation. */
    @JsonProperty("paginate_data")
    public PaginateData paginateData() {
        return new PaginateData(total, pageSize, page, totalPages);
    }

    /** 1-based index of the first record on this page. */
    @JsonIgnore
    public long getFrom() {
        if (total == 0) return 0;
        return (long) (page - 1) * pageSize + 1;
    }

    /** 1-based index of the last record on this page. */
    @JsonIgnore
    public long getTo() {
        return Math.min((long) page * pageSize, total);
    }

    /** Whether a previous page exists. */
    public boolean hasPrev() {
        return page > 1;
    }

    /** Whether a next page exists. */
    public boolean hasNext() {
        return page < totalPages;
    }

    /** Previous page number, or 1 if already on the first page. */
    public int prevPage() {
        return Math.max(1, page - 1);
    }

    /** Next page number, or {@code totalPages} if already on the last page. */
    public int nextPage() {
        return Math.min(totalPages, page + 1);
    }
}
