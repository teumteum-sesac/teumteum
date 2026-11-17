package com.teumteum.place.domain;

import static com.teumteum.place.domain.PlaceCategory.*;

import com.teumteum.place.service.KakaoCategory;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 세분류와 카카오 검색·분류 규칙 (REQ-016).
 * 이름(name)은 DB category.category_name 의 세분류 이름과 같다.
 * 검색 출처(sources)는 화면설계서 v2.0 RES-01 '데이터 출처' 표를 따른다.
 *
 * 분류 규칙: 카카오 category_name("음식점 > 카페 > 커피전문점")에 tokens 중 하나가 들어 있으면 그 세분류로 본다.
 * 선언 순서대로 검사하므로 구체적인 것을 먼저 둔다 (보드카페·방탈출카페가 '카페'보다 먼저).
 * 어느 세분류에도 맞지 않으면 후보에서 제외한다 (임의 분류 금지, 저장 코스 세분류 필수 #31).
 *
 * TODO: tokens 는 실제 카카오 응답으로 확인 후 보정한다 (화면설계서 '개발하면서 확인할 것').
 */
public enum SubCategory {

    // ===== 오락/체험: 키워드(방탈출·보드게임카페·공방·노래방). 카페보다 먼저 검사 =====
    ESCAPE_ROOM(ENTERTAINMENT, "방탈출", of(kw("방탈출")), "방탈출"),
    BOARD_GAME(ENTERTAINMENT, "보드게임", of(kw("보드게임카페")), "보드카페", "보드게임"),
    WORKSHOP(ENTERTAINMENT, "공방", of(kw("공방")), "공방"),
    KARAOKE(ENTERTAINMENT, "노래방", of(kw("노래방")), "노래방"),

    // ===== 카페/디저트: CE7 + 키워드(베이커리·디저트) =====
    DESSERT(CAFE, "디저트", of(kw("디저트")), "디저트"),
    BAKERY(CAFE, "베이커리", of(kw("베이커리")), "제과", "베이커리"),
    CAFE_SUB(CAFE, "카페", of(group(KakaoCategory.CE7)), "카페"),

    // ===== 음식/맛집: FD6 + 키워드(한식·양식 등) =====
    KOREAN(FOOD, "한식", of(group(KakaoCategory.FD6), kw("한식")), "한식"),
    WESTERN(FOOD, "양식", of(group(KakaoCategory.FD6), kw("양식")), "양식"),
    JAPANESE(FOOD, "일식", of(group(KakaoCategory.FD6), kw("일식")), "일식"),
    CHINESE(FOOD, "중식", of(group(KakaoCategory.FD6), kw("중식")), "중식"),
    SNACK(FOOD, "분식", of(group(KakaoCategory.FD6), kw("분식")), "분식"),

    // ===== 문화생활: CT1 + 키워드(영화관·서점) =====
    // TODO: 전시는 문화포털, 공연은 KOPIS 매일 1회 저장 데이터로 보완 (별도 이슈). 그 전까지 CT1 결과로 분류한다.
    EXHIBITION(CULTURE, "전시", of(group(KakaoCategory.CT1)), "미술관", "전시관", "갤러리", "박물관"),
    MOVIE(CULTURE, "영화", of(group(KakaoCategory.CT1), kw("영화관")), "영화관"),
    PERFORMANCE(CULTURE, "공연", of(group(KakaoCategory.CT1)), "공연장", "연극극장"),
    BOOKSTORE(CULTURE, "책방", of(kw("서점")), "서점", "책방"),

    // ===== 자연/휴식: 키워드(공원·산책로·한강공원) + AT4. 한강공원이 공원보다 먼저 =====
    HAN_RIVER(NATURE, "한강", of(kw("한강공원")), "한강공원"),
    PARK(NATURE, "공원", of(kw("공원"), group(KakaoCategory.AT4)), "공원"),
    TRAIL(NATURE, "산책로", of(kw("산책로")), "산책로", "둘레길"),

    // ===== 쇼핑/구경: 키워드(소품샵·팝업스토어·시장·백화점) =====
    POPUP(SHOPPING, "팝업스토어", of(kw("팝업스토어")), "팝업"),
    GOODS_SHOP(SHOPPING, "소품샵", of(kw("소품샵")), "소품"),
    MARKET(SHOPPING, "시장", of(kw("시장")), "시장"),
    DEPARTMENT_STORE(SHOPPING, "백화점", of(kw("백화점")), "백화점"),

    // ===== 스포츠활동: 키워드(당구장·볼링장·클라이밍) =====
    BILLIARDS(SPORTS, "당구", of(kw("당구장")), "당구"),
    BOWLING(SPORTS, "볼링", of(kw("볼링장")), "볼링"),
    CLIMBING(SPORTS, "클라이밍", of(kw("클라이밍")), "클라이밍"),

    // ===== 관광/명소: AT4 (사진·소개는 TourAPI, REQ-017) =====
    PALACE(TOURISM, "고궁", of(group(KakaoCategory.AT4)), "고궁"),
    OBSERVATORY(TOURISM, "전망대", of(group(KakaoCategory.AT4)), "전망대"),
    // TODO: 고궁·전망대가 아닌 관광명소를 '랜드마크'로 볼지 팀 확인 필요
    LANDMARK(TOURISM, "랜드마크", of(group(KakaoCategory.AT4)), "관광,명소");

    private final PlaceCategory parent;
    private final String name;
    private final List<PlaceSearchSource> sources;
    private final List<String> tokens;

    SubCategory(PlaceCategory parent, String name, List<PlaceSearchSource> sources, String... tokens) {
        this.parent = parent;
        this.name = name;
        this.sources = sources;
        this.tokens = List.of(tokens);
    }

    private static List<PlaceSearchSource> of(PlaceSearchSource... sources) {
        return List.of(sources);
    }

    private static PlaceSearchSource group(KakaoCategory group) {
        return PlaceSearchSource.group(group);
    }

    private static PlaceSearchSource kw(String keyword) {
        return PlaceSearchSource.keyword(keyword);
    }

    public PlaceCategory getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    /** 선택한 대분류들을 검색하려면 호출해야 할 카카오 검색 목록 (중복 제거) */
    public static Set<PlaceSearchSource> sourcesOf(Set<PlaceCategory> categories) {
        Set<PlaceSearchSource> result = new LinkedHashSet<>();
        for (SubCategory sub : values()) {
            if (categories.contains(sub.parent)) {
                result.addAll(sub.sources);
            }
        }
        return result;
    }

    /** 카카오 category_name 으로 세분류를 찾는다. 맞는 것이 없으면 빈 값 */
    public static Optional<SubCategory> classify(String kakaoCategoryName) {
        if (kakaoCategoryName == null || kakaoCategoryName.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(sub -> sub.tokens.stream().anyMatch(kakaoCategoryName::contains))
                .findFirst();
    }
}