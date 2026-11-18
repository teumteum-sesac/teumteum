package com.teumteum.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.domain.Place;
import com.teumteum.place.domain.PlaceCategory;
import com.teumteum.place.dto.PlaceRegisterCommand;
import com.teumteum.place.repository.PlaceRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    private PlaceRegistrar placeRegistrar;
    private PlaceService placeService;

    @BeforeEach
    void setUp() {
        placeRegistrar = new PlaceRegistrar(placeRepository);
        placeService = new PlaceService(placeRegistrar);
    }

    @Test
    void 이미_있는_장소는_등록하지_않고_기존_ID를_돌려준다() {
        given(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "111"))
                .willReturn(Optional.of(place(7L, "111")));

        Long id = placeService.findOrCreate(cafe("111"));

        assertThat(id).isEqualTo(7L);
        verify(placeRepository, never()).saveAndFlush(any());
        verify(placeRepository, never()).insertPlaceCategory(anyLong(), anyLong());
    }

    @Test
    void 없는_장소는_등록하고_세분류를_연결한_뒤_새_ID를_돌려준다() {
        given(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "222")).willReturn(Optional.empty());
        given(placeRepository.findSubCategoryId("CAFE", "카페")).willReturn(Optional.of(13L));
        given(placeRepository.saveAndFlush(any(Place.class))).willReturn(place(8L, "222"));

        Long id = placeService.findOrCreate(cafe("222"));

        assertThat(id).isEqualTo(8L);
        verify(placeRepository).insertPlaceCategory(8L, 13L);
    }

    @Test
    void 동시에_먼저_등록된_장소면_다시_조회해서_그_ID를_돌려준다() {
        given(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "333"))
                .willReturn(Optional.empty())                  // 처음 조회: 없음
                .willReturn(Optional.of(place(9L, "333")));    // 재조회: 다른 요청이 등록함
        given(placeRepository.findSubCategoryId("CAFE", "카페")).willReturn(Optional.of(13L));
        given(placeRepository.saveAndFlush(any(Place.class)))
                .willThrow(new DataIntegrityViolationException("UK_place_external"));

        assertThat(placeService.findOrCreate(cafe("333"))).isEqualTo(9L);
    }

    @Test
    void 세분류를_찾을_수_없으면_예외를_던지고_등록하지_않는다() {
        given(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "444")).willReturn(Optional.empty());
        given(placeRepository.findSubCategoryId(anyString(), anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> placeService.findOrCreate(cafe("444"))).isInstanceOf(CustomException.class);
        verify(placeRepository, never()).saveAndFlush(any());
    }

    @Test
    void 좌표가_범위를_벗어나면_예외를_던진다() {
        PlaceRegisterCommand invalid = new PlaceRegisterCommand(
                "555", "장소", PlaceCategory.CAFE, "카페", "서울 성동구", 91, 127.0557);

        assertThatThrownBy(() -> placeService.findOrCreate(invalid)).isInstanceOf(CustomException.class);
    }

    @Test
    void 여러_장소를_한_번에_처리하고_같은_장소는_한_번만_조회한다() {
        given(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "111"))
                .willReturn(Optional.of(place(7L, "111")));
        given(placeRepository.findByProviderAndExternalPlaceId("KAKAO", "666"))
                .willReturn(Optional.of(place(10L, "666")));

        Map<String, Long> ids = placeService.findOrCreateAll(List.of(cafe("111"), cafe("666"), cafe("111")));

        assertThat(ids).containsExactly(Map.entry("111", 7L), Map.entry("666", 10L));
        verify(placeRepository, times(1)).findByProviderAndExternalPlaceId("KAKAO", "111");
    }

    // ===== helpers =====

    private static PlaceRegisterCommand cafe(String kakaoPlaceId) {
        return new PlaceRegisterCommand(kakaoPlaceId, "장소" + kakaoPlaceId, PlaceCategory.CAFE, "카페",
                "서울 성동구", 37.5445, 127.0557);
    }

    private static Place place(Long id, String kakaoPlaceId) {
        Place place = Place.kakao(kakaoPlaceId, "장소" + kakaoPlaceId, "서울 성동구", 37.5445, 127.0557);
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }
}
