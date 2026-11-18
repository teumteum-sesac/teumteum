package com.teumteum.course.service;

import com.teumteum.course.dto.*;
import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import com.teumteum.place.dto.PlaceLatestLookup;
import com.teumteum.place.dto.PlaceLatestSource;
import com.teumteum.place.service.PlaceLatestService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CourseLatestService {
    private final CourseService courses;
    private final PlaceLatestService places;
    private final Duration budget;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    // 동시 요청이 늘어도 실제 외부 조회 작업 수를 제한한다. 남는 요청은 항목별 FAILED다.
    private final Semaphore permits = new Semaphore(8);

    @Autowired
    public CourseLatestService(CourseService courses, PlaceLatestService places) {
        this(courses, places, Duration.ofSeconds(7));
    }

    CourseLatestService(CourseService courses, PlaceLatestService places, Duration budget) {
        this.courses = courses;
        this.places = places;
        this.budget = budget;
    }

    // 외부 호출을 기다리는 동안 DB 트랜잭션·커넥션을 점유하지 않는다.
    public CourseLatestPlacesResponse getLatest(Long memberId, Long courseId) {
        // 소유권을 먼저 확인한다. 다른 회원 코스라면 외부 조회 작업도 시작하지 않는다.
        CourseResponse saved = courses.getCourse(memberId, courseId);
        if (saved.places().isEmpty() || saved.places().size() > 4) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
        OffsetDateTime requestedAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
        long deadline = System.nanoTime() + budget.toNanos();
        // 원본 ID는 DB 연결 정보에서 가져오며 클라이언트 입력으로 받지 않는다.
        List<PlaceLatestSource> sources = saved.places().stream()
                .map(p -> places.source(Long.valueOf(p.placeId()))).toList();
        var futures = new ArrayList<Future<PlaceLatestLookup>>();
        for (PlaceLatestSource source : sources) {
            futures.add(executor.submit(() -> {
                if (!permits.tryAcquire()) return PlaceLatestLookup.failed();
                try {
                    return places.lookup(source);
                } finally {
                    permits.release();
                }
            }));
        }
        var results = new ArrayList<CourseLatestPlaceResponse>();
        for (int i = 0; i < futures.size(); i++) {
            PlaceLatestLookup lookup;
            Future<PlaceLatestLookup> future = futures.get(i);
            try {
                lookup = future.isDone() ? future.get()
                        : future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                lookup = PlaceLatestLookup.failed();
            } catch (ExecutionException | TimeoutException failed) {
                future.cancel(true);
                lookup = PlaceLatestLookup.failed();
            }
            if (lookup == null) lookup = PlaceLatestLookup.failed();
            results.add(result(saved.places().get(i), lookup));
        }
        return new CourseLatestPlacesResponse(saved.courseId(), requestedAt, results);
    }

    private static CourseLatestPlaceResponse result(CoursePlaceResponse saved, PlaceLatestLookup lookup) {
        String errorCode = switch (lookup.status()) {
            case SUCCESS -> null;
            case NOT_FOUND -> "PLACE_INFORMATION_NOT_FOUND";
            case FAILED -> "PLACE_LOOKUP_FAILED";
        };
        String message = switch (lookup.status()) {
            case SUCCESS -> null;
            case NOT_FOUND -> "장소 정보를 확인할 수 없어요.";
            case FAILED -> "최신 정보를 불러오지 못했어요.";
        };
        return new CourseLatestPlaceResponse(saved.coursePlaceId(), saved.placeId(), saved.visitOrder(),
                lookup.status().name(), lookup.fetchedAt(), lookup.place(), errorCode, message);
    }

    @PreDestroy
    public void close() { executor.shutdownNow(); }
}
