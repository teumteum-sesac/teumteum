package com.teumteum.recommend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.teumteum.recommend.domain.CategoryCode.CULTURE;
import static com.teumteum.recommend.domain.CategoryCode.ENTERTAINMENT;
import static com.teumteum.recommend.domain.CategoryCode.FOOD;
import static com.teumteum.recommend.domain.CategoryCode.NATURE;
import static com.teumteum.recommend.domain.CategoryCode.SHOPPING;
import static com.teumteum.recommend.domain.CategoryCode.SPORTS;
import static com.teumteum.recommend.domain.CategoryCode.TOURISM;

/**
 * 세분류 29개와 예상 체류시간(분) (화면설계서 RES-01 체류시간표).
 * <p>
 * category: 이 세분류가 속한 대분류. 세분류 필터를 대분류별로 묶을 때 쓴다 (REQ-050).
 * 맨 위 import static 덕분에 CategoryCode.CULTURE 대신 CULTURE 만 써도 된다.
 */
@Getter
@RequiredArgsConstructor
public enum SubCategory {
    // 1. 값 목록 (소속 대분류, 이름, 체류시간)
    // 문화생활
    EXHIBITION(CULTURE, "전시", 40),
    MOVIE(CULTURE, "영화", 150),
    PERFORMANCE(CULTURE, "공연", 150),
    BOOKSTORE(CULTURE, "책방", 40),
    // 카페/디저트
    // 이 enum 안에서 CAFE 라고만 쓰면 대분류가 아니라 세분류 SubCategory.CAFE(자기 자신)를 가리킨다.
    // 그래서 카페/디저트 줄만 CategoryCode.CAFE 로 이름을 다 적는다.
    CAFE(CategoryCode.CAFE, "카페", 40),
    BAKERY(CategoryCode.CAFE, "베이커리", 40),
    DESSERT(CategoryCode.CAFE, "디저트", 40),
    // 음식/맛집
    KOREAN(FOOD, "한식", 40),
    WESTERN(FOOD, "양식", 40),
    JAPANESE(FOOD, "일식", 40),
    CHINESE(FOOD, "중식", 40),
    SNACK(FOOD, "분식", 40),
    // 자연/휴식
    PARK(NATURE, "공원", 40),
    WALKING_TRAIL(NATURE, "산책로", 40),
    HAN_RIVER(NATURE, "한강", 40),
    // 오락/체험
    ESCAPE_ROOM(ENTERTAINMENT, "방탈출", 40),
    BOARD_GAME(ENTERTAINMENT, "보드게임", 40),
    WORKSHOP(ENTERTAINMENT, "공방", 40),
    KARAOKE(ENTERTAINMENT, "노래방", 40),
    // 쇼핑/구경
    PROP_SHOP(SHOPPING, "소품샵", 40),
    POPUP_STORE(SHOPPING, "팝업스토어", 40),
    MARKET(SHOPPING, "시장", 40),
    DEPARTMENT_STORE(SHOPPING, "백화점", 40),
    // 스포츠활동
    BILLIARDS(SPORTS, "당구", 40),
    BOWLING(SPORTS, "볼링", 40),
    CLIMBING(SPORTS, "클라이밍", 40),
    // 관광/명소
    LANDMARK(TOURISM, "랜드마크", 40),
    PALACE(TOURISM, "고궁", 40),
    OBSERVATORY(TOURISM, "전망대", 40);

    private final CategoryCode category;
    private final String displayName;
    private final int stayMinutes;

    public static Optional<SubCategory> fromDisplayName(String name) {
        // ① name 이 null 이면 → 바로 "없음" 돌려주기
        if (name == null) return Optional.empty();

        // ② values() 로 29개를 하나씩 돌면서
        //    getDisplayName() 이 name 과 같으면 → 그 값을 Optional 에 담아 돌려주기
        for (SubCategory sub : values()) {
            if (sub.getDisplayName().equals(name)) {
                return Optional.of(sub);
            }
        }
        // ③ 끝까지 못 찾으면 → "없음" 돌려주기
        return Optional.empty();

    }

    /**
     * 한 대분류에 속한 세분류들을 선언 순서대로 돌려준다.
     * 예) SPORTS → [당구, 볼링, 클라이밍]
     */
    public static List<SubCategory> of(CategoryCode category) {
        List<SubCategory> result = new ArrayList<>();
        for (SubCategory sub : values()) {
            if (sub.getCategory() == category) {
                result.add(sub);
            }
        }
        return result;
    }
}
