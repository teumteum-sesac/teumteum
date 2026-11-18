package com.teumteum.recommend.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.recommend.exception.RecommendErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 다시 추천 횟수 제한 (recommend-api.md 2장, REQ-026).
 * <p>
 * 같은 사용자(지금은 IP 주소)가 <b>최근 1분 동안 5번</b>까지 허용하고, 6번째부터 429 TOO_MANY_REQUESTS 를 던진다.
 * 외부 API(장소 검색·이동시간) 무료 한도를 지키기 위한 제한이다.
 * <pre>
 * 사용자별로 "허용한 요청 시각" 목록을 기억한다.
 *   13:00:10, 13:00:20, 13:00:30, 13:00:40, 13:00:50  ← 5개
 *   13:01:05 요청 → 60초 전(13:00:05) 이후 기록이 5개 → 429
 *   13:01:15 요청 → 13:00:10 은 60초가 지나 빠짐 → 4개 → 허용
 * </pre>
 * 메모리에만 보관하므로 서버를 다시 켜면 횟수도 처음부터 센다 (추천 보관소와 같은 방식).
 */
@Component
public class RegenerateRateLimiter {

    /** 1분 안에 허용하는 횟수 */
    static final int MAX_REQUESTS = 5;

    /** 횟수를 세는 기간 */
    static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * 사용자 구분 값(IP) → 최근 1분 안에 허용한 요청 시각 (오래된 순).
     * <p>
     * ConcurrentHashMap: 여러 요청이 동시에 들어와도 안전한 Map.
     * 값은 바꿀 수 없는 List 로 두고, 바꿀 때마다 새 List 로 통째로 갈아 끼운다.
     * → 다른 스레드가 읽는 도중에 목록이 바뀌는 일이 없다.
     */
    private final Map<String, List<Instant>> requestTimes = new ConcurrentHashMap<>();

    private final Clock clock;

    /** 스프링이 쓰는 생성자 (실제 시계). 시각 비교만 하므로 시간대는 상관없다 */
    @Autowired
    public RegenerateRateLimiter() {
        this(Clock.systemUTC());
    }

    /** 테스트용 생성자 (package-private). 시계를 움직여 "1분 뒤" 를 만든다 */
    RegenerateRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 이번 요청을 허용할지 확인하고, 허용하면 기록한다.
     *
     * @param clientKey 사용자 구분 값 (IP 주소)
     * @throws CustomException 최근 1분 안에 이미 5번 허용했으면 429 TOO_MANY_REQUESTS (이번 요청은 기록하지 않는다)
     */
    public void check(String clientKey) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);

        removeIdle(windowStart);

        // compute: 같은 키에 대해서는 한 번에 한 요청만 이 블록을 실행한다 (동시에 6번째가 2개 통과하는 일 방지).
        // 블록 안에서 예외를 던지면 Map 은 바뀌지 않고 예외만 밖으로 나간다.
        requestTimes.compute(clientKey, (key, times) -> {
            List<Instant> recent = recentOnly(times, windowStart);
            if (recent.size() >= MAX_REQUESTS) {
                throw new CustomException(RecommendErrorCode.TOO_MANY_REQUESTS);
            }
            List<Instant> updated = new ArrayList<>(recent);
            updated.add(now);
            return List.copyOf(updated);
        });
    }

    /** 기록 중 1분 안의 것만 남긴다. 처음 온 사용자면(null) 빈 목록 */
    private List<Instant> recentOnly(List<Instant> times, Instant windowStart) {
        if (times == null) {
            return List.of();
        }
        return times.stream()
                .filter(time -> time.isAfter(windowStart))
                .toList();
    }

    /**
     * 1분 넘게 요청이 없는 사용자는 Map 에서 지운다 (안 지우면 한 번 온 IP 가 계속 쌓인다).
     * 마지막 요청 시각만 보면 된다: 목록이 오래된 순이라 마지막이 1분 지났으면 전부 지난 것이다.
     * <p>
     * 요청마다 Map 전체를 한 번 훑는다. 최근 1분 안에 온 사용자 수만큼이라 이 서비스 규모에서는 충분히 가볍다.
     */
    private void removeIdle(Instant windowStart) {
        requestTimes.entrySet().removeIf(entry -> !entry.getValue().getLast().isAfter(windowStart));
    }

    /** 테스트용: 지금 기록을 들고 있는 사용자 수 */
    int trackedClientCount() {
        return requestTimes.size();
    }
}
