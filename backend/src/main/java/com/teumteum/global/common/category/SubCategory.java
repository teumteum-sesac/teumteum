package com.teumteum.global.common.category;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.teumteum.global.common.category.Category.CULTURE;
import static com.teumteum.global.common.category.Category.ENTERTAINMENT;
import static com.teumteum.global.common.category.Category.FOOD;
import static com.teumteum.global.common.category.Category.NATURE;
import static com.teumteum.global.common.category.Category.SHOPPING;
import static com.teumteum.global.common.category.Category.SPORTS;
import static com.teumteum.global.common.category.Category.TOURISM;

/**
 * 세분류 29개: 소속 대분류 · 이름 · 예상 체류시간 최소~최대 (화면설계서 RES-01 표). 추천·장소·코스가 모두 이 enum 하나를 쓴다 (#174).
 * <p>
 * 여기에는 "이 세분류가 무엇인지" 만 둔다.
 * "카카오에서 어떻게 찾는지"(검색 키워드·카카오 분류 코드)는 장소 검색에만 쓰는 정보라
 * 장소 쪽 {@code place.domain.SubCategorySearchRule} 에 따로 둔다.
 * <p>
 * 다른 곳(DB category_name, 응답의 subCategory)에는 enum 이름이 아니라 displayName("카페")이 들어간다.
 * 순서는 화면설계서 표 순서이고, 세분류 필터 칩도 이 순서로 나온다.
 */
@Getter
@RequiredArgsConstructor
public enum SubCategory {
    // 문화생활
    EXHIBITION(CULTURE, "전시", 40, 90),
    MOVIE(CULTURE, "영화", 150, 180),
    PERFORMANCE(CULTURE, "공연", 150, 180),
    BOOKSTORE(CULTURE, "책방", 40, 90),
    // 카페/디저트
    // 이 enum 안에서 CAFE 라고만 쓰면 대분류가 아니라 세분류 SubCategory.CAFE(자기 자신)를 가리킨다.
    // 그래서 카페/디저트 줄만 Category.CAFE 로 이름을 다 적는다.
    CAFE(Category.CAFE, "카페", 40, 90),
    BAKERY(Category.CAFE, "베이커리", 40, 90),
    DESSERT(Category.CAFE, "디저트", 40, 90),
    // 음식/맛집
    KOREAN(FOOD, "한식", 40, 90),
    WESTERN(FOOD, "양식", 40, 90),
    JAPANESE(FOOD, "일식", 40, 90),
    CHINESE(FOOD, "중식", 40, 90),
    SNACK(FOOD, "분식", 40, 90),
    // 자연/휴식
    PARK(NATURE, "공원", 40, 90),
    WALKING_TRAIL(NATURE, "산책로", 40, 90),
    HAN_RIVER(NATURE, "한강", 40, 90),
    // 오락/체험
    ESCAPE_ROOM(ENTERTAINMENT, "방탈출", 40, 90),
    BOARD_GAME(ENTERTAINMENT, "보드게임", 40, 90),
    WORKSHOP(ENTERTAINMENT, "공방", 40, 90),
    KARAOKE(ENTERTAINMENT, "노래방", 40, 90),
    // 쇼핑/구경
    PROP_SHOP(SHOPPING, "소품샵", 40, 90),
    POPUP_STORE(SHOPPING, "팝업스토어", 40, 90),
    MARKET(SHOPPING, "시장", 40, 90),
    DEPARTMENT_STORE(SHOPPING, "백화점", 40, 90),
    // 스포츠활동
    BILLIARDS(SPORTS, "당구", 40, 90),
    BOWLING(SPORTS, "볼링", 40, 90),
    CLIMBING(SPORTS, "클라이밍", 40, 90),
    // 관광/명소
    LANDMARK(TOURISM, "랜드마크", 40, 90),
    PALACE(TOURISM, "고궁", 40, 90),
    OBSERVATORY(TOURISM, "전망대", 40, 90);

    /** 소속 대분류 */
    private final Category category;
    /** 세분류 이름 (예: "카페"). DB·응답에는 이 값이 들어간다 */
    private final String displayName;
    /**
     * 최소 체류시간(분). 코스를 고를 때는 이 값으로 어림한다. 영화·공연 150분, 나머지 40분 (2026-11-18 팀 결정)
     */
    private final int minStayMinutes;
    /**
     * 최대 체류시간(분). 코스를 확정하고 남는 시간이 있으면 이 값까지 체류시간을 늘린다.
     * 영화·공연 180분, 나머지 90분 (#204, 2026-11-19 팀 결정)
     */
    private final int maxStayMinutes;

    /** 이름("카페")으로 세분류를 찾는다. 없거나 null 이면 빈 값 */
    public static Optional<SubCategory> fromDisplayName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (SubCategory sub : values()) {
            if (sub.getDisplayName().equals(name)) {
                return Optional.of(sub);
            }
        }
        return Optional.empty();
    }

    /**
     * 한 대분류에 속한 세분류들을 선언 순서대로 돌려준다.
     * 예) SPORTS → [당구, 볼링, 클라이밍]
     */
    public static List<SubCategory> of(Category category) {
        List<SubCategory> result = new ArrayList<>();
        for (SubCategory sub : values()) {
            if (sub.getCategory() == category) {
                result.add(sub);
            }
        }
        return result;
    }
}
