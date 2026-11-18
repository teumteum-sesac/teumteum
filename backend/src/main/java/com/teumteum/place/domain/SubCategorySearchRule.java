package com.teumteum.place.domain;

import com.teumteum.global.common.category.Category;
import com.teumteum.global.common.category.SubCategory;
import com.teumteum.place.service.KakaoCategory;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 세분류마다 카카오에서 어떻게 찾고 어떻게 분류하는지 (REQ-016). 장소 검색에서만 쓴다.
 * <p>
 * 세분류 자체(이름·대분류·체류시간)는 공통 {@link SubCategory} 에 있고, 이 enum 은 그 세분류를 "카카오에서 찾는 규칙" 만 갖는다.
 * 예전에는 장소 쪽 SubCategory 하나에 둘 다 있었는데, 추천과 같은 세분류를 공통으로 쓰게 되면서 나눴다 (#174).
 * 공통 enum 에 카카오 검색 규칙까지 넣으면 공통 코드가 장소 검색 구현(카카오)에 묶이기 때문이다.
 * <p>
 * 검색 출처(sources)는 화면설계서 v2.0 RES-01 '데이터 출처' 표를 따른다.
 * 분류 규칙: 카카오 category_name("음식점 > 카페 > 커피전문점")에 tokens 중 하나가 들어 있으면 그 세분류로 본다.
 * 선언 순서대로 검사하므로 구체적인 것을 먼저 둔다 (보드카페·방탈출카페가 '카페'보다 먼저).
 * 어느 세분류에도 맞지 않으면 후보에서 제외한다 (임의 분류 금지, 저장 코스 세분류 필수 #31).
 *
 * TODO: tokens 는 실제 카카오 응답으로 확인 후 보정한다 (화면설계서 '개발하면서 확인할 것').
 */
public enum SubCategorySearchRule {

    // ===== 오락/체험: 키워드(방탈출·보드게임카페·공방·노래방). 카페보다 먼저 검사 =====
    ESCAPE_ROOM(SubCategory.ESCAPE_ROOM, of(kw("방탈출")), "방탈출"),
    BOARD_GAME(SubCategory.BOARD_GAME, of(kw("보드게임카페")), "보드카페", "보드게임"),
    WORKSHOP(SubCategory.WORKSHOP, of(kw("공방")), "공방"),
    KARAOKE(SubCategory.KARAOKE, of(kw("노래방")), "노래방"),

    // ===== 카페/디저트: CE7 + 키워드(베이커리·디저트) =====
    DESSERT(SubCategory.DESSERT, of(kw("디저트")), "디저트"),
    BAKERY(SubCategory.BAKERY, of(kw("베이커리")), "제과", "베이커리"),
    CAFE(SubCategory.CAFE, of(group(KakaoCategory.CE7)), "카페"),

    // ===== 음식/맛집: FD6 + 키워드(한식·양식 등) =====
    KOREAN(SubCategory.KOREAN, of(group(KakaoCategory.FD6), kw("한식")), "한식"),
    WESTERN(SubCategory.WESTERN, of(group(KakaoCategory.FD6), kw("양식")), "양식"),
    JAPANESE(SubCategory.JAPANESE, of(group(KakaoCategory.FD6), kw("일식")), "일식"),
    CHINESE(SubCategory.CHINESE, of(group(KakaoCategory.FD6), kw("중식")), "중식"),
    SNACK(SubCategory.SNACK, of(group(KakaoCategory.FD6), kw("분식")), "분식"),

    // ===== 문화생활: CT1 + 키워드(영화관·서점) =====
    // TODO: 전시는 문화포털, 공연은 KOPIS 매일 1회 저장 데이터로 보완 (별도 이슈). 그 전까지 CT1 결과로 분류한다.
    EXHIBITION(SubCategory.EXHIBITION, of(group(KakaoCategory.CT1)), "미술관", "전시관", "갤러리", "박물관"),
    MOVIE(SubCategory.MOVIE, of(group(KakaoCategory.CT1), kw("영화관")), "영화관"),
    PERFORMANCE(SubCategory.PERFORMANCE, of(group(KakaoCategory.CT1)), "공연장", "연극극장"),
    BOOKSTORE(SubCategory.BOOKSTORE, of(kw("서점")), "서점", "책방"),

    // ===== 자연/휴식: 키워드(공원·산책로·한강공원) + AT4. 한강공원이 공원보다 먼저 =====
    HAN_RIVER(SubCategory.HAN_RIVER, of(kw("한강공원")), "한강공원"),
    PARK(SubCategory.PARK, of(kw("공원"), group(KakaoCategory.AT4)), "공원"),
    WALKING_TRAIL(SubCategory.WALKING_TRAIL, of(kw("산책로")), "산책로", "둘레길"),

    // ===== 쇼핑/구경: 키워드(소품샵·팝업스토어·시장·백화점) =====
    POPUP_STORE(SubCategory.POPUP_STORE, of(kw("팝업스토어")), "팝업"),
    PROP_SHOP(SubCategory.PROP_SHOP, of(kw("소품샵")), "소품"),
    MARKET(SubCategory.MARKET, of(kw("시장")), "시장"),
    DEPARTMENT_STORE(SubCategory.DEPARTMENT_STORE, of(kw("백화점")), "백화점"),

    // ===== 스포츠활동: 키워드(당구장·볼링장·클라이밍) =====
    BILLIARDS(SubCategory.BILLIARDS, of(kw("당구장")), "당구"),
    BOWLING(SubCategory.BOWLING, of(kw("볼링장")), "볼링"),
    CLIMBING(SubCategory.CLIMBING, of(kw("클라이밍")), "클라이밍"),

    // ===== 관광/명소: AT4 (사진·소개는 TourAPI, REQ-017) =====
    PALACE(SubCategory.PALACE, of(group(KakaoCategory.AT4)), "고궁"),
    OBSERVATORY(SubCategory.OBSERVATORY, of(group(KakaoCategory.AT4)), "전망대"),
    // TODO: 고궁·전망대가 아닌 관광명소를 '랜드마크'로 볼지 팀 확인 필요
    LANDMARK(SubCategory.LANDMARK, of(group(KakaoCategory.AT4)), "관광,명소");

    private final SubCategory subCategory;
    private final List<PlaceSearchSource> sources;
    private final List<String> tokens;

    SubCategorySearchRule(SubCategory subCategory, List<PlaceSearchSource> sources, String... tokens) {
        this.subCategory = subCategory;
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

    /** 이 규칙이 찾는 세분류 (공통 enum) */
    public SubCategory getSubCategory() {
        return subCategory;
    }

    /** 선택한 대분류들을 검색하려면 호출해야 할 카카오 검색 목록 (중복 제거) */
    public static Set<PlaceSearchSource> sourcesOf(Set<Category> categories) {
        Set<PlaceSearchSource> result = new LinkedHashSet<>();
        for (SubCategorySearchRule rule : values()) {
            if (categories.contains(rule.subCategory.getCategory())) {
                result.addAll(rule.sources);
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
                .filter(rule -> rule.tokens.stream().anyMatch(kakaoCategoryName::contains))
                .map(rule -> rule.subCategory)
                .findFirst();
    }
}
