package com.teumteum.recommend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CategoryCode {
    CULTURE("문화생활"),
    CAFE("카페/디저트"),
    FOOD("음식/맛집"),
    NATURE("자연/휴식"),
    ENTERTAINMENT("오락/체험"),
    SHOPPING("쇼핑/구경"),
    SPORTS("스포츠활동"),
    TOURISM("관광/명소");

    private final String displayName;
}
