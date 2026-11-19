package com.teumteum.recommend.dto;

import com.teumteum.global.common.category.Category;
import com.teumteum.recommend.domain.CategoryMode;
import com.teumteum.recommend.domain.TransportType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // 정상값
    private static final LocationRequest ORIGIN = new LocationRequest("성수역", null, 37.5446, 127.0559);
    private static final List<Category> CATEGORIES = List.of(Category.SPORTS, Category.SHOPPING);

    /** 바꿔 볼 필드만 받고 나머지는 정상값으로 채운 요청 */
    private RecommendRequest createRequest(LocationRequest origin, String startTime,
                                           Integer availableMinutes, List<Category> categories) {
        return new RecommendRequest(origin, null, startTime, availableMinutes,
                TransportType.TRANSIT, CategoryMode.DIRECT, categories);
    }

    /** 위반 목록에 이 필드가 있는지 */
    private boolean hasViolationOn(Set<ConstraintViolation<RecommendRequest>> violations, String field) {
        for (ConstraintViolation<RecommendRequest> violation : violations) {
            if (violation.getPropertyPath().toString().equals(field)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("정상 요청은 통과한다 (도착지 없음 포함)")
    void validate_passes_whenRequestIsValid() {
        // given
        RecommendRequest request = createRequest(ORIGIN, "13:00", 180, CATEGORIES);

        // when
        Set<ConstraintViolation<RecommendRequest>> violations = validator.validate(request);

        // then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("이용 시간이 0분이면 실패한다")
    void validate_fails_whenAvailableMinutesIsZero() {
        RecommendRequest request = createRequest(ORIGIN, "13:00", 0, CATEGORIES);

        Set<ConstraintViolation<RecommendRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "availableMinutes"));
    }

    @Test
    @DisplayName("이용 시간이 360분을 넘으면 실패한다")
    void validate_fails_whenAvailableMinutesIsOver360() {
        RecommendRequest request = createRequest(ORIGIN, "13:00", 390, CATEGORIES);

        Set<ConstraintViolation<RecommendRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "availableMinutes"));
    }

    @Test
    @DisplayName("출발지가 없으면 실패한다")
    void validate_fails_whenOriginIsNull() {
        RecommendRequest request = createRequest(null, "13:00", 180, CATEGORIES);

        Set<ConstraintViolation<RecommendRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "origin"));
    }

    @Test
    @DisplayName("출발지 위도가 범위를 벗어나면 실패한다 (@Valid 로 안쪽까지 검사)")
    void validate_fails_whenOriginLatitudeIsOutOfRange() {
        LocationRequest wrongOrigin = new LocationRequest("성수역", null, 91.0, 127.0559);
        RecommendRequest request = createRequest(wrongOrigin, "13:00", 180, CATEGORIES);

        Set<ConstraintViolation<RecommendRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "origin.latitude"));
    }

    @Test
    @DisplayName("시작 시각이 30분 단위가 아니면 실패한다")
    void validate_fails_whenStartTimeIsNot30MinuteUnit() {
        RecommendRequest request = createRequest(ORIGIN, "13:15", 180, CATEGORIES);

        Set<ConstraintViolation<RecommendRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "startTime"));
    }

    @Test
    @DisplayName("대분류를 4개 이상 고르면 실패한다")
    void validate_fails_whenCategoriesAreMoreThan3() {
        List<Category> fourCategories = List.of(
                Category.SPORTS, Category.SHOPPING, Category.CAFE, Category.FOOD);
        RecommendRequest request = createRequest(ORIGIN, "13:00", 180, fourCategories);

        Set<ConstraintViolation<RecommendRequest>> violations = validator.validate(request);

        assertTrue(hasViolationOn(violations, "categories"));
    }
}