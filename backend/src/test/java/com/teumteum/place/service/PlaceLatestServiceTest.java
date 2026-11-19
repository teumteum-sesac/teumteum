package com.teumteum.place.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.teumteum.place.domain.Place;
import com.teumteum.place.dto.*;
import com.teumteum.place.dto.kakao.KakaoPlaceResponse;
import com.teumteum.place.repository.PlaceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaceLatestServiceTest {
    final PlaceRepository repository = mock(PlaceRepository.class);
    final KakaoLocalClient kakao = mock(KakaoLocalClient.class);
    final PlaceDetailService details = mock(PlaceDetailService.class);
    final PlaceLatestService service = new PlaceLatestService(repository, kakao, details);
    final PlaceLatestSource source = new PlaceLatestSource("external-1", "옛 이름", 37.5, 127.1);

    @Test
    void readsSourceFromInternalIdWithoutCallingExternalApis() {
        given(repository.findById(42L)).willReturn(Optional.of(Place.kakao("external-1", "옛 이름", null, 37.5, 127.1)));
        assertThat(service.source(42L)).isEqualTo(source);
        verifyNoInteractions(kakao, details);
        verify(repository, never()).save(any());
    }

    @Test
    void usesOnlyCurrentExactIdResultAndPreservesPhotoAttribution() {
        given(kakao.searchKeyword("옛 이름", 127.1, 37.5, 1, 15))
                .willReturn(new KakaoPlaceResponse(null, List.of(document("other", "동명이인", "127.0", "37.0"),
                        document("external-1", "새 이름", "127.2", "37.6"))));
        given(details.getDetail("external-1", "새 이름", 37.6, 127.2))
                .willReturn(new PlaceDetailResponse("external-1", true, "https://image/new.jpg", "Type1",
                        "새 소개", "10~20", null, "https://place.map.kakao.com/external-1"));
        var result = service.lookup(source);
        assertThat(result.status()).isEqualTo(PlaceLatestLookup.Status.SUCCESS);
        assertThat(result.fetchedAt()).isNotNull();
        assertThat(result.place().placeName()).isEqualTo("새 이름");
        assertThat(result.place().category()).isEqualTo("CAFE");
        assertThat(result.place().subCategory()).isEqualTo("카페");
        assertThat(result.place().address()).isEqualTo("새 도로명");
        assertThat(result.place().latitude()).isEqualTo(37.6);
        assertThat(result.place().imageSource()).isEqualTo("한국관광공사 TourAPI");
        assertThat(result.place().imageCopyrightType()).isEqualTo("Type1");
        verifyNoInteractions(repository);
    }

    @Test
    void missingExactIdDoesNotReturnAnotherPlaceOrOldValues() {
        given(kakao.searchKeyword("옛 이름", 127.1, 37.5, 1, 15))
                .willReturn(new KakaoPlaceResponse(null, List.of(document("different", "옛 이름", "127.1", "37.5"))));
        var result = service.lookup(source);
        assertThat(result.status()).isEqualTo(PlaceLatestLookup.Status.NOT_FOUND);
        assertThat(result.place()).isNull();
        assertThat(result.fetchedAt()).isNull();
        verifyNoInteractions(details);
    }

    @Test
    void invalidCoordinatePairIsNullAndDoesNotUseSourceCoordinates() {
        given(kakao.searchKeyword("옛 이름", 127.1, 37.5, 1, 15))
                .willReturn(new KakaoPlaceResponse(null, List.of(document("external-1", "새 이름", "NaN", "37.5"))));
        var result = service.lookup(source);
        assertThat(result.status()).isEqualTo(PlaceLatestLookup.Status.SUCCESS);
        assertThat(result.place().latitude()).isNull();
        assertThat(result.place().longitude()).isNull();
        assertThat(result.place().imageUrl()).isNull();
        verifyNoInteractions(details);
    }

    private KakaoPlaceResponse.Document document(String id, String name, String x, String y) {
        return new KakaoPlaceResponse.Document(id, name, "음식점 > 카페", "CE7", null, null,
                "새 지번", "새 도로명", x, y, null, "0");
    }
}
