package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.recommend.exception.RecommendErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 다시 추천 횟수 제한 테스트.
 * <p>
 * Clock.fixed 는 시각이 멈춘 시계라 "1분 뒤" 를 만들 수 없다.
 * 그래서 아래 MovableClock 처럼 테스트에서 시각을 앞으로 옮길 수 있는 시계를 직접 만든다.
 */
class RegenerateRateLimiterTest {

    private static final String IP_A = "203.0.113.1";
    private static final String IP_B = "203.0.113.2";

    private final MovableClock clock = new MovableClock(Instant.parse("2026-11-17T04:00:00Z"));
    private final RegenerateRateLimiter limiter = new RegenerateRateLimiter(clock);

    @Test
    @DisplayName("1분 안에 5번까지 허용하고 6번째는 429 TOO_MANY_REQUESTS")
    void allowsFive_thenRejectsSixth() {
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.check(IP_A));
            clock.advance(Duration.ofSeconds(1));
        }

        CustomException thrown = assertThrows(CustomException.class, () -> limiter.check(IP_A));
        assertEquals(RecommendErrorCode.TOO_MANY_REQUESTS, thrown.getErrorCode());
    }

    @Test
    @DisplayName("가장 오래된 요청이 1분 지나면 다시 1번 허용한다 (최근 1분 기준)")
    void allowsAgain_afterOldestLeavesWindow() {
        for (int i = 0; i < 5; i++) {
            limiter.check(IP_A);
            clock.advance(Duration.ofSeconds(10)); // 0, 10, 20, 30, 40초에 요청
        }
        // 지금 50초. 0초 요청은 아직 1분 안 → 막힘
        assertThrows(CustomException.class, () -> limiter.check(IP_A));

        clock.advance(Duration.ofSeconds(11)); // 61초: 0초 요청이 1분 지남
        assertDoesNotThrow(() -> limiter.check(IP_A));
        // 10초 요청은 아직 1분 안이라 다시 5개 → 바로 다음은 막힘
        assertThrows(CustomException.class, () -> limiter.check(IP_A));
    }

    @Test
    @DisplayName("정확히 1분 된 요청은 '최근 1분' 에서 빠진다")
    void exactlyOneMinuteOld_isNotCounted() {
        for (int i = 0; i < 5; i++) {
            limiter.check(IP_A); // 모두 같은 시각
        }

        clock.advance(Duration.ofMinutes(1));

        assertDoesNotThrow(() -> limiter.check(IP_A));
    }

    @Test
    @DisplayName("막힌 요청은 기록하지 않는다 (계속 눌러도 막히는 시간이 늘어나지 않음)")
    void rejectedRequests_areNotRecorded() {
        for (int i = 0; i < 5; i++) {
            limiter.check(IP_A);
        }
        clock.advance(Duration.ofSeconds(30));
        for (int i = 0; i < 3; i++) {
            assertThrows(CustomException.class, () -> limiter.check(IP_A)); // 30초에 3번 더 눌러 막힘
        }

        clock.advance(Duration.ofSeconds(30)); // 처음 5번이 1분 지남

        // 막힌 3번이 기록됐다면 여기서도 막힌다
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.check(IP_A));
        }
    }

    @Test
    @DisplayName("IP 가 다르면 따로 센다")
    void countsSeparatelyPerClient() {
        for (int i = 0; i < 5; i++) {
            limiter.check(IP_A);
        }

        assertThrows(CustomException.class, () -> limiter.check(IP_A));
        assertDoesNotThrow(() -> limiter.check(IP_B));
    }

    @Test
    @DisplayName("1분 넘게 요청이 없는 사용자 기록은 지운다 (기록이 계속 쌓이지 않음)")
    void removesIdleClients() {
        limiter.check(IP_A);
        limiter.check(IP_B);
        assertEquals(2, limiter.trackedClientCount());

        clock.advance(Duration.ofMinutes(2));
        limiter.check(IP_B); // 요청이 들어올 때 정리한다

        assertEquals(1, limiter.trackedClientCount()); // A 는 지워지고 B 만 남음
    }

    /** 테스트에서 시각을 앞으로 옮길 수 있는 시계 */
    private static class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
