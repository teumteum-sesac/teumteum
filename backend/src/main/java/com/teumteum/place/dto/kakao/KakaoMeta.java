package com.teumteum.place.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoMeta(
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("pageable_count") int pageableCount,
        @JsonProperty("is_end") boolean isEnd
) {
}
