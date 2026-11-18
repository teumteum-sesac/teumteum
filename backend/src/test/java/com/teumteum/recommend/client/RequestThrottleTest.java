package com.teumteum.recommend.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 호출 간격 조절 테스트.
 * <p>
 * 실제로 기다리면 테스트가 느려지므로, 가짜 시계(now)와 가짜 기다리기(sleeps 에 기록 + 시계를 그만큼 앞으로)를 넣는다.
 */
class RequestThrottleTest {

    private static final long MS = 1_000_000L; // 1ms = 1,000,000ns

    private long now = 0;                              // 가짜 시계 (나노초)
    private final List<Long> sleeps = new ArrayList<>(); // 기다린 시간(ms) 기록

    private final RequestThrottle throttle = new RequestThrottle(
            Duration.ofMillis(300),
            () -> now,
            millis -> {
                sleeps.add(millis);
                now += millis * MS; // 기다린 만큼 시간이 흐른다
            });

    @Test
    @DisplayName("첫 호출은 기다리지 않는다")
    void firstCall_doesNotWait() {
        throttle.acquire();

        assertTrue(sleeps.isEmpty());
    }

    @Test
    @DisplayName("직전 호출 50ms 뒤에 부르면 남은 250ms(+여유 1ms) 를 기다린다")
    void waitsRemainingInterval() {
        throttle.acquire();
        now += 50 * MS;

        throttle.acquire();

        assertEquals(List.of(251L), sleeps);
    }

    @Test
    @DisplayName("직전 호출에서 300ms 넘게 지났으면 기다리지 않는다")
    void doesNotWait_whenIntervalPassed() {
        throttle.acquire();
        now += 400 * MS;

        throttle.acquire();

        assertTrue(sleeps.isEmpty());
    }

    @Test
    @DisplayName("연달아 3번 부르면 호출 사이가 모두 300ms 이상 벌어진다")
    void keepsIntervalForBurst() {
        List<Long> passedAt = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            throttle.acquire();
            passedAt.add(now);
        }

        assertTrue(passedAt.get(1) - passedAt.get(0) >= 300 * MS);
        assertTrue(passedAt.get(2) - passedAt.get(1) >= 300 * MS);
    }
}
