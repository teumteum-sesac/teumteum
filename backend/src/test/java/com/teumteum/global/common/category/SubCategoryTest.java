package com.teumteum.global.common.category;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 세분류 ↔ 대분류 소속 테스트 (세분류 필터를 대분류별로 묶을 때 쓴다).
 */
class SubCategoryTest {

    @Test
    @DisplayName("스포츠활동의 세분류는 당구·볼링·클라이밍이다 (선언 순서)")
    void of_sports() {
        List<String> names = SubCategory.of(Category.SPORTS).stream()
                .map(SubCategory::getDisplayName)
                .toList();

        assertEquals(List.of("당구", "볼링", "클라이밍"), names);
    }

    @Test
    @DisplayName("세분류 '카페' 는 대분류 카페/디저트(CAFE) 소속이다")
    void cafe_belongsToCafeCategory() {
        assertEquals(Category.CAFE, SubCategory.CAFE.getCategory());
    }

    @Test
    @DisplayName("대분류 8개 모두 세분류가 하나 이상 있고, 다 합치면 29개다")
    void everyCategoryHasSubCategories() {
        int total = 0;
        for (Category category : Category.values()) {
            List<SubCategory> subs = SubCategory.of(category);
            assertFalse(subs.isEmpty(), category + " 에 세분류가 없음");
            total += subs.size();
        }
        assertEquals(29, total);
    }
}
