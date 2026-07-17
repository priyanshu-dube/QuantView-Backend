package com.quantview.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for watchlist items returned by the API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistItemDTO {

    private Long id;
    private String userId;
    private String ticker;
    private String addedAt;
}
