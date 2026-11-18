package com.teumteum.place.domain;

import com.teumteum.global.common.category.Category;
import com.teumteum.global.common.category.SubCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 장소 검색 규칙 테스트 (#174 에서 공통 SubCategory 와 나누면서 추가).
 * 나누기 전과 분류 결과가 같은지, 공통 세분류 29개가 빠짐없이 규칙을 가지는지 본다.
 */
class SubCategorySearchRuleTest {

    @Test
    @DisplayName("공통 세분류 29개는 모두 검색 규칙이 정확히 하나씩 있다 (새 세분류를 만들고 규칙을 빠뜨리면 실패)")
    void everySubCategoryHasOneRule() {
        Set<SubCategory> covered = EnumSet.noneOf(SubCategory.class);
        for (SubCategorySearchRule rule : SubCategorySearchRule.values()) {
            assertTrue(covered.add(rule.getSubCategory()), "규칙이 두 개: " + rule.getSubCategory());
        }
        assertEquals(EnumSet.allOf(SubCategory.class), covered);
    }

    @Test
    @DisplayName("카카오 분류 이름으로 공통 세분류를 찾는다")
    void classify() {
        assertEquals(Optional.of(SubCategory.CAFE), SubCategorySearchRule.classify("음식점 > 카페 > 커피전문점"));
        assertEquals(Optional.of(SubCategory.KOREAN), SubCategorySearchRule.classify("음식점 > 한식 > 육류,고기"));
        assertEquals(Optional.of(SubCategory.HAN_RIVER), SubCategorySearchRule.classify("여행 > 공원 > 한강공원"));
    }

    @Test
    @DisplayName("구체적인 규칙이 먼저: 보드카페는 '카페' 가 아니라 보드게임, 한강공원은 '공원' 이 아니라 한강")
    void classify_checksSpecificRuleFirst() {
        assertEquals(Optional.of(SubCategory.BOARD_GAME), SubCategorySearchRule.classify("음식점 > 카페 > 테마카페 > 보드카페"));
        assertEquals(Optional.of(SubCategory.HAN_RIVER), SubCategorySearchRule.classify("여행 > 공원 > 한강공원"));
    }

    @Test
    @DisplayName("맞는 규칙이 없거나 비어 있으면 빈 값 (후보에서 제외)")
    void classify_unknown() {
        assertFalse(SubCategorySearchRule.classify("서비스,산업 > 사우나").isPresent());
        assertFalse(SubCategorySearchRule.classify(null).isPresent());
        assertFalse(SubCategorySearchRule.classify(" ").isPresent());
    }

    @Test
    @DisplayName("대분류로 카카오 검색 목록을 모으면 그 대분류 세분류들의 출처만 들어간다")
    void sourcesOf() {
        assertFalse(SubCategorySearchRule.sourcesOf(Set.of(Category.CAFE)).isEmpty());
        assertTrue(SubCategorySearchRule.sourcesOf(Set.of()).isEmpty());
    }
}
