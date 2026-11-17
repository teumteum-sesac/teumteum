package com.teumteum.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.domain.PlaceCategory;
import com.teumteum.place.dto.NearbyPlaceResult;
import com.teumteum.place.dto.kakao.KakaoPlaceResponse;
import com.teumteum.place.dto.kakao.KakaoPlaceResponse.Document;
import com.teumteum.place.exception.PlaceErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NearbyPlaceServiceTest {

    private static final double LAT = 37.5445;
    private static final double LNG = 127.0557;
    private static final int RADIUS = 1_000;

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    @InjectMocks
    private NearbyPlaceService nearbyPlaceService;

    @Test
    void 카페를_선택하면_세분류를_붙여_가까운_순으로_돌려준다() {
        givenCategory(KakaoCategory.CE7, many(5, "음식점 > 카페 > 커피전문점"));
        givenAnyKeyword(response());

        List<NearbyPlaceResult> results = find(Set.of(PlaceCategory.CAFE));

        assertThat(results).hasSize(5);
        assertThat(results.get(0).category()).isEqualTo(PlaceCategory.CAFE);
        assertThat(results.get(0).subCategory()).isEqualTo("카페");
        assertThat(results).isSortedAccordingTo((a, b) -> a.distance() - b.distance());
    }

    @Test
    void 요청한_반경으로_카카오를_호출한다() {
        givenCategory(KakaoCategory.FD6, response());
        givenAnyKeyword(response());

        nearbyPlaceService.findCandidates(LAT, LNG, Set.of(PlaceCategory.FOOD), 3_000);

        verify(kakaoLocalClient).searchCategory(eq(KakaoCategory.FD6), anyDouble(), anyDouble(),
                eq(3_000), anyInt(), anyInt());
    }

    @Test
    void 카테고리를_고르지_않으면_대분류_구분_없이_조회한다() {
        given(kakaoLocalClient.searchCategory(any(), anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt()))
                .willReturn(response(doc("1", "음식점 > 한식", "100")));
        given(kakaoLocalClient.searchKeyword(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt()))
                .willReturn(response(doc("2", "여행 > 공원", "200")));

        List<NearbyPlaceResult> results = find(Set.of());

        assertThat(results).extracting(NearbyPlaceResult::category)
                .containsExactly(PlaceCategory.FOOD, PlaceCategory.NATURE);
    }

    @Test
    void 보드카페는_보드게임으로_분류되어_카페만_선택하면_제외된다() {
        givenCategory(KakaoCategory.CE7, response(doc("1", "음식점 > 카페 > 테마카페 > 보드카페", "100")));
        givenAnyKeyword(response());

        assertThat(find(Set.of(PlaceCategory.CAFE))).isEmpty();
    }

    @Test
    void 세분류가_정해지지_않는_장소는_제외한다() {
        givenCategory(KakaoCategory.FD6, response(doc("1", "음식점 > 술집 > 호프", "100")));
        givenAnyKeyword(response());

        assertThat(find(Set.of(PlaceCategory.FOOD))).isEmpty();
    }

    @Test
    void 같은_장소가_여러_검색에서_나와도_한_번만_돌려준다() {
        Document same = doc("1", "음식점 > 카페 > 디저트카페", "100");
        givenCategory(KakaoCategory.CE7, response(same));
        givenAnyKeyword(response(same));

        List<NearbyPlaceResult> results = find(Set.of(PlaceCategory.CAFE));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).subCategory()).isEqualTo("디저트");
    }

    @Test
    void 일부_검색만_실패하면_나머지_결과로_응답한다() {
        givenCategory(KakaoCategory.CE7, many(3, "음식점 > 카페"));
        given(kakaoLocalClient.searchKeyword(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt()))
                .willThrow(new CustomException(PlaceErrorCode.KAKAO_API_ERROR));

        assertThat(find(Set.of(PlaceCategory.CAFE))).hasSize(3);
    }

    @Test
    void 모든_검색이_실패하면_예외를_던진다() {
        given(kakaoLocalClient.searchCategory(any(), anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt()))
                .willThrow(new CustomException(PlaceErrorCode.KAKAO_API_ERROR));
        given(kakaoLocalClient.searchKeyword(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt()))
                .willThrow(new CustomException(PlaceErrorCode.KAKAO_API_ERROR));

        assertThatThrownBy(() -> find(Set.of(PlaceCategory.FOOD))).isInstanceOf(CustomException.class);
    }

    @Test
    void 카테고리가_3개를_넘으면_예외를_던진다() {
        assertThatThrownBy(() -> find(Set.of(PlaceCategory.CAFE, PlaceCategory.FOOD,
                PlaceCategory.NATURE, PlaceCategory.SPORTS)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 좌표나_반경이_범위를_벗어나면_예외를_던진다() {
        assertThatThrownBy(() -> nearbyPlaceService.findCandidates(91, LNG, Set.of(PlaceCategory.CAFE), RADIUS))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> nearbyPlaceService.findCandidates(LAT, LNG, Set.of(PlaceCategory.CAFE), 0))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> nearbyPlaceService.findCandidates(LAT, LNG, Set.of(PlaceCategory.CAFE), 20_001))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void RES01_출처대로_음식은_FD6와_세분류_키워드로_검색한다() {
        givenCategory(KakaoCategory.FD6, response());
        givenAnyKeyword(response());

        find(Set.of(PlaceCategory.FOOD));

        verify(kakaoLocalClient).searchKeyword(eq("한식"), anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt());
        verify(kakaoLocalClient).searchKeyword(eq("분식"), anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt());
    }

    // ===== helpers =====

    private List<NearbyPlaceResult> find(Set<PlaceCategory> categories) {
        return nearbyPlaceService.findCandidates(LAT, LNG, categories, RADIUS);
    }

    private void givenCategory(KakaoCategory group, KakaoPlaceResponse response) {
        given(kakaoLocalClient.searchCategory(eq(group), anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt()))
                .willReturn(response);
    }

    private void givenAnyKeyword(KakaoPlaceResponse response) {
        given(kakaoLocalClient.searchKeyword(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt(), anyInt()))
                .willReturn(response);
    }

    private static KakaoPlaceResponse response(Document... docs) {
        return new KakaoPlaceResponse(null, List.of(docs));
    }

    private static KakaoPlaceResponse many(int count, String categoryName) {
        List<Document> docs = new ArrayList<>();
        for (int i = count; i >= 1; i--) { // 일부러 먼 순서로 넣어 정렬 확인
            docs.add(doc(String.valueOf(i), categoryName, String.valueOf(i * 100)));
        }
        return new KakaoPlaceResponse(null, docs);
    }

    private static Document doc(String id, String categoryName, String distance) {
        return new Document(id, "장소" + id, categoryName, null, null, null,
                "서울 성동구", null, "127.0557", "37.5445", "http://place.map.kakao.com/" + id, distance);
    }
}