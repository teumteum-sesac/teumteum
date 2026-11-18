package com.teumteum.place.repository;

import com.teumteum.place.domain.Place;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /** (place_provider, external_place_id) 유니크 키로 조회 */
    Optional<Place> findByProviderAndExternalPlaceId(String provider, String externalPlaceId);

    /**
     * 대분류 코드 + 세분류 이름으로 세분류 category_id 를 찾는다.
     * category 테이블은 erd.md 4절 기본 데이터로 채워진다.
     */
    @Query(value = """
            SELECT c.category_id
              FROM category c
              JOIN category p ON c.parent_category_id = p.category_id
             WHERE p.category_code = :categoryCode
               AND c.category_name = :subCategoryName
               AND c.category_level = 2
            """, nativeQuery = true)
    Optional<Long> findSubCategoryId(@Param("categoryCode") String categoryCode,
                                     @Param("subCategoryName") String subCategoryName);

    /** place_category(장소 ↔ 세분류) 연결. 복합 키 테이블이라 엔티티 없이 넣는다 */
    @Modifying
    @Query(value = "INSERT INTO place_category (place_id, category_id) VALUES (:placeId, :categoryId)",
            nativeQuery = true)
    void insertPlaceCategory(@Param("placeId") Long placeId, @Param("categoryId") Long categoryId);
}
