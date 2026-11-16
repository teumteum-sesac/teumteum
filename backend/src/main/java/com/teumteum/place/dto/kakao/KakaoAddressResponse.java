package com.teumteum.place.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 주소 검색 응답. road_address 는 도로명 주소가 없으면 null 이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoAddressResponse(KakaoMeta meta, List<Document> documents) {

    public List<Document> documentsOrEmpty() {
        return documents == null ? List.of() : documents;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            @JsonProperty("address_name") String addressName,
            @JsonProperty("address_type") String addressType,
            String x,
            String y,
            JibunAddress address,
            @JsonProperty("road_address") RoadAddress roadAddress
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JibunAddress(@JsonProperty("address_name") String addressName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoadAddress(
            @JsonProperty("address_name") String addressName,
            @JsonProperty("building_name") String buildingName
    ) {
    }
}
