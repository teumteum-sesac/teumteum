package com.teumteum.global.common.category;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 대분류 8개 (화면설계서 v2.0, AGENTS.md 7장). 추천·장소·코스가 모두 이 enum 하나를 쓴다.
 * <p>
 * 예전에는 추천(CategoryCode)과 장소(PlaceCategory)에 같은 enum 이 따로 있어서
 * {@code PlaceCategory.valueOf(category.name())} 같은 변환 코드가 여러 곳에 있었다.
 * 공통으로 옮겨 변환을 없애고, 대분류를 바꿀 때 한 곳만 고치면 되게 했다 (#174, 팀 회의 결정).
 * <p>
 * 요청·응답 JSON 과 DB 에는 이름(name(), 예: "CAFE")이 그대로 들어간다. 값 이름을 바꾸면 API·DB 가 깨지니 주의.
 */
@Getter
@RequiredArgsConstructor
public enum Category {
    CULTURE("문화생활"),
    CAFE("카페/디저트"),
    FOOD("음식/맛집"),
    NATURE("자연/휴식"),
    ENTERTAINMENT("오락/체험"),
    SHOPPING("쇼핑/구경"),
    SPORTS("스포츠활동"),
    TOURISM("관광/명소");

    /** 화면 이름 (예: "카페/디저트") */
    private final String displayName;
}
