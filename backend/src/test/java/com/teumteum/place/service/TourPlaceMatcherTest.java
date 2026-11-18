package com.teumteum.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.teumteum.place.dto.tour.TourPlace;
import java.util.List;
import org.junit.jupiter.api.Test;

class TourPlaceMatcherTest {

    @Test
    void 이름이_같으면_매칭한다() {
        TourPlace tour = tour("1", "39", "할머니냉면", 13.0);

        assertThat(TourPlaceMatcher.findBest("할머니냉면", List.of(tour))).contains(tour);
    }

    @Test
    void 이름이_포함되기만_하면_다른_장소로_본다() {
        TourPlace tour = tour("1", "39", "할머니냉면", 1_000.0);

        assertThat(TourPlaceMatcher.findBest("청량리할머니냉면 용두직영점", List.of(tour))).isEmpty();
    }

    @Test
    void 띄어_쓴_지점명과_공백은_무시한다() {
        TourPlace tour = tour("1", "39", "할머니 냉면", 20.0);

        assertThat(TourPlaceMatcher.findBest("할머니냉면 제기본점", List.of(tour))).contains(tour);
    }

    @Test
    void 백화점은_지점명으로_보지_않는다() {
        assertThat(TourPlaceMatcher.normalize("현대 백화점")).isEqualTo("현대백화점");
    }

    @Test
    void 숙박은_제외한다() {
        TourPlace stay = tour("1", "32", "효자스테이", 10.0);

        assertThat(TourPlaceMatcher.findBest("효자스테이", List.of(stay))).isEmpty();
    }

    @Test
    void 같은_이름이_여러_개면_가장_가까운_것을_고른다() {
        TourPlace far = tour("1", "39", "할머니냉면", 800.0);
        TourPlace near = tour("2", "39", "할머니냉면", 13.0);

        assertThat(TourPlaceMatcher.findBest("할머니냉면", List.of(far, near))).contains(near);
    }

    private static TourPlace tour(String id, String type, String title, Double distance) {
        return new TourPlace(id, type, title, distance, null, null);
    }
}
