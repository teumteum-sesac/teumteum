package com.teumteum.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.dto.PlaceDetailResponse;
import com.teumteum.place.dto.tour.TourCommon;
import com.teumteum.place.dto.tour.TourIntro;
import com.teumteum.place.dto.tour.TourPlace;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceDetailServiceTest {

    private static final String KAKAO_ID = "10926416";
    private static final double LAT = 37.5822701;
    private static final double LNG = 127.0437006;

    @Mock
    private TourApiClient tourApiClient;

    @InjectMocks
    private PlaceDetailService placeDetailService;

    @Test
    void TourAPI에서_찾으면_사진_소개_운영시간을_돌려준다() {
        given(tourApiClient.findNearby(LNG, LAT, PlaceDetailService.MATCH_RADIUS)).willReturn(List.of(
                new TourPlace("2842974", "39", "할머니냉면", 13.0, "http://image/1.jpg", "Type3")));
        given(tourApiClient.findCommon("2842974")).willReturn(Optional.of(new TourCommon("함흥냉면 전문점", null)));
        given(tourApiClient.findIntro("2842974", "39")).willReturn(Optional.of(new TourIntro("10:30~21:00", "연중무휴")));

        PlaceDetailResponse result = placeDetailService.getDetail(KAKAO_ID, "할머니냉면", LAT, LNG);

        assertThat(result.tourInfoAvailable()).isTrue();
        assertThat(result.imageUrl()).isEqualTo("http://image/1.jpg");
        assertThat(result.imageCopyrightType()).isEqualTo("Type3");
        assertThat(result.overview()).isEqualTo("함흥냉면 전문점");
        assertThat(result.openingHours()).isEqualTo("10:30~21:00");
        assertThat(result.restDate()).isEqualTo("연중무휴");
        assertThat(result.kakaoMapUrl()).isEqualTo("https://place.map.kakao.com/10926416");
    }

    @Test
    void 목록에_사진이_없으면_공통정보의_사진을_쓴다() {
        given(tourApiClient.findNearby(LNG, LAT, PlaceDetailService.MATCH_RADIUS)).willReturn(List.of(
                new TourPlace("1", "12", "할머니냉면", 13.0, null, "Type1")));
        given(tourApiClient.findCommon("1")).willReturn(Optional.of(new TourCommon(null, "http://image/common.jpg")));
        given(tourApiClient.findIntro("1", "12")).willReturn(Optional.empty());

        PlaceDetailResponse result = placeDetailService.getDetail(KAKAO_ID, "할머니냉면", LAT, LNG);

        assertThat(result.imageUrl()).isEqualTo("http://image/common.jpg");
        assertThat(result.openingHours()).isNull();
    }

    @Test
    void TourAPI에_없으면_카카오맵_링크만_돌려준다() {
        given(tourApiClient.findNearby(LNG, LAT, PlaceDetailService.MATCH_RADIUS)).willReturn(List.of(
                new TourPlace("1", "39", "청량리함흥냉면", 30.0, "http://image/x.jpg", "Type3")));

        PlaceDetailResponse result = placeDetailService.getDetail(KAKAO_ID, "할머니냉면", LAT, LNG);

        assertThat(result.tourInfoAvailable()).isFalse();
        assertThat(result.imageUrl()).isNull();
        assertThat(result.kakaoMapUrl()).isEqualTo("https://place.map.kakao.com/10926416");
        verify(tourApiClient, never()).findCommon(anyString());
    }

    @Test
    void TourAPI_장애로_빈_결과면_카카오맵_링크만_돌려준다() {
        given(tourApiClient.findNearby(anyDouble(), anyDouble(), anyInt())).willReturn(List.of());

        PlaceDetailResponse result = placeDetailService.getDetail(KAKAO_ID, "할머니냉면", LAT, LNG);

        assertThat(result.tourInfoAvailable()).isFalse();
        assertThat(result.kakaoMapUrl()).isNotNull();
    }

    @Test
    void 좌표가_범위를_벗어나면_예외를_던진다() {
        assertThatThrownBy(() -> placeDetailService.getDetail(KAKAO_ID, "할머니냉면", 91, LNG))
                .isInstanceOf(CustomException.class);
    }
}
