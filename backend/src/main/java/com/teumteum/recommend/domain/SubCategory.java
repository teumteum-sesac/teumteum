package com.teumteum.recommend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum SubCategory {
    // 1. 값 목록
    // 문화생활
    EXHIBITION("전시", 90),
    MOVIE("영화", 150),
    PERFORMANCE("공연", 150),
    BOOKSTORE("책방", 60),
    // 카페/디저트
    CAFE("카페", 60),
    BAKERY("베이커리", 30),
    DESSERT("디저트", 40),
    // 음식/맛집
    KOREAN("한식", 60),
    WESTERN("양식", 60),
    JAPANESE("일식", 60),
    CHINESE("중식", 60),
    SNACK("분식", 40),
    // 자연/휴식
    PARK("공원", 60),
    WALKING_TRAIL("산책로", 60),
    HAN_RIVER("한강", 90),
    // 오락/체험
    ESCAPE_ROOM("방탈출", 90),
    BOARD_GAME("보드게임", 120),
    WORKSHOP("공방", 120),
    KARAOKE("노래방", 60),
    // 쇼핑/구경
    PROP_SHOP("소품샵", 40),
    POPUP_STORE("팝업스토어", 40),
    MARKET("시장", 60),
    DEPARTMENT_STORE("백화점", 90),
    // 스포츠활동
    BILLIARDS("당구", 90),
    BOWLING("볼링", 90),
    CLIMBING("클라이밍", 120),
    // 관광/명소
    LANDMARK("랜드마크", 60),
    PALACE("고궁", 90),
    OBSERVATORY("전망대", 60);

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

}
