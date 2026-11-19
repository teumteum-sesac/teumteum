package com.teumteum.place.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 키워드·카테고리 검색 응답. x = 경도, y = 위도, distance 는 중심 좌표를 준 경우에만 값이 있다(m).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoPlaceResponse(KakaoMeta meta, List<Document> documents) {

    public List<Document> documentsOrEmpty() {
        return documents == null ? List.of() : documents;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("category_group_code") String categoryGroupCode,
            @JsonProperty("category_group_name") String categoryGroupName,
            String phone,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            String x,
            String y,
            @JsonProperty("place_url") String placeUrl,
            String distance
    ) {
    }
}
