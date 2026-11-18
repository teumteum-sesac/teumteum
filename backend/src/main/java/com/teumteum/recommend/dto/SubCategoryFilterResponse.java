package com.teumteum.recommend.dto;

import com.teumteum.global.common.category.Category;

import java.util.List;

/**
 * 세분류 필터 칩 묶음 1개 = 대분류 1개 + 그 아래 세분류들 (REQ-050, RES-01).
 * <pre>
 * { "category": "SPORTS", "categoryName": "스포츠활동", "subCategories": ["당구", "볼링", "클라이밍"] }
 * </pre>
 * 대분류별로 묶어 두면 프론트가 두 방식 모두 그릴 수 있다.
 * - 두 단계: [전체][스포츠활동][쇼핑/구경] → 스포츠활동 누르면 [당구][볼링][클라이밍]
 * - 한 줄: 묶음을 펼쳐서 [전체][당구][볼링][클라이밍][소품샵]...
 * 걸러 보기(어떤 칩을 눌렀을 때 어떤 코스를 보여줄지)는 프론트가 코스 장소의 subCategory 로 한다.
 * <p>
 * 명세 1.3 은 아직 문자열 목록(["당구", ...])으로 적혀 있다. 다음 문서 이슈에서 이 형식으로 고친다.
 *
 * @param category      대분류 코드 (예: SPORTS)
 * @param categoryName  대분류 화면 이름 (예: "스포츠활동")
 * @param subCategories 세분류 이름들, 세분류 표 순서
 */
public record SubCategoryFilterResponse(
        Category category,
        String categoryName,
        List<String> subCategories
) {
}
