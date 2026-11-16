package com.teumteum.place.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 좌표→주소 변환 응답. 바다·산 등은 documents 가 비어 있을 수 있고, road_address 는 자주 null 이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoCoord2AddressResponse(List<Document> documents) {

    public List<Document> documentsOrEmpty() {
        return documents == null ? List.of() : documents;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            @JsonProperty("road_address") KakaoAddressResponse.RoadAddress roadAddress,
            KakaoAddressResponse.JibunAddress address
    ) {
    }
}
