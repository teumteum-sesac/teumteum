package com.teumteum.recommend.client;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * 외부 API 호출 사이에 최소 간격을 두는 도구 (ODsay 429 Too Many Requests 방지).
 * <pre>
 * 간격 300ms 일 때
 *   1번째 호출 0ms   → 바로 통과
 *   2번째 호출 50ms  → 250ms 기다렸다가 통과 (300ms 시점)
 *   3번째 호출 900ms → 이미 300ms 넘게 지났으니 바로 통과
 * </pre>
 * synchronized: 여러 사용자의 요청이 동시에 와도 한 번에 한 스레드만 들어온다.
 * 기다리는 동안 다른 스레드도 줄을 서서 기다리므로, 서버 전체에서 호출 간격이 지켜진다.
 * <p>
 * 시간 재기(nanoTime)와 기다리기(sleep)를 밖에서 받을 수 있게 해서, 테스트는 실제로 기다리지 않고 확인한다.
 */
class RequestThrottle {

    /** 기다리는 방법. 실제로는 Thread.sleep, 테스트에서는 "얼마나 기다렸는지" 기록만 한다 */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final long minIntervalNanos;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;

    /** 마지막으로 통과한 시각. 아직 한 번도 안 불렀으면 null */
    private Long lastPassedNanos;

    RequestThrottle(Duration minInterval) {
        this(minInterval, System::nanoTime, Thread::sleep);
    }

    /** 테스트용 생성자 */
    RequestThrottle(Duration minInterval, LongSupplier nanoTime, Sleeper sleeper) {
        this.minIntervalNanos = minInterval.toNanos();
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    /** 직전 호출에서 최소 간격이 안 지났으면 남은 만큼 기다린 뒤 돌아온다. API 를 부르기 직전에 부른다 */
    synchronized void acquire() {
        long now = nanoTime.getAsLong();
        if (lastPassedNanos != null) {
            long waitNanos = lastPassedNanos + minIntervalNanos - now;
            if (waitNanos > 0) {
                sleepQuietly(Duration.ofNanos(waitNanos).toMillis() + 1); // 밀리초로 내림되는 만큼 1ms 여유
                now = nanoTime.getAsLong();
            }
        }
        lastPassedNanos = now;
    }

    private void sleepQuietly(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            // 서버가 꺼지는 중 등에 깨워진 경우. "깨워졌다" 표시를 다시 켜 두고 그냥 진행한다
            Thread.currentThread().interrupt();
        }
    }
}
