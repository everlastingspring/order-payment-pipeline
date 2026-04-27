package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated list wrapper returned by all collection endpoints.
 *
 * <p>Mirrors the structure of Spring Data's {@code Page} so controllers
 * can construct it from {@code Page.getContent()}, {@code Page.getNumber()}, etc.</p>
 *
 * @param <T> the element type of the page content
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResponse<T> {

    /** The items on the current page. */
    private List<T> content;

    /** Zero-based current page number. */
    private int page;

    /** Requested page size (max items per page). */
    private int size;

    /** Total number of items across all pages. */
    private long totalElements;

    /** Total number of pages available ({@code ceil(totalElements / size)}). */
    private int totalPages;

    /** {@code true} if this is the first page ({@code page == 0}). */
    private boolean first;

    /** {@code true} if this is the last page ({@code page == totalPages - 1}). */
    private boolean last;
}
