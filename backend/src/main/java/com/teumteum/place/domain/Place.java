package com.teumteum.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공용 장소 (place 테이블). 외부(카카오) 장소를 한 번만 저장해 두고 여러 코스·추천 기록이 참조한다 (NFR-010).
 * 저장한 코스의 장소 정보는 course_place 에 따로 복사하므로, 여기 값은 처음 등록한 값을 유지한다.
 */
@Entity
@Table(name = "place")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    public static final String PROVIDER_KAKAO = "KAKAO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id;

    @Column(name = "place_provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "external_place_id", nullable = false, length = 150)
    private String externalPlaceId;

    @Column(name = "place_name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    /** DB 기본값(30분)을 쓰므로 INSERT 에 넣지 않는다. 실제 체류시간은 세분류(category.stay_minutes) 기준 */
    @Column(name = "default_stay_minutes", insertable = false, updatable = false)
    private Short defaultStayMinutes;

    /** TourAPI 연동(REQ-017) 후 채운다 */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** TourAPI 연동(REQ-017) 후 채운다 */
    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    private Place(String provider, String externalPlaceId, String name, String address,
                  BigDecimal latitude, BigDecimal longitude) {
        this.provider = provider;
        this.externalPlaceId = externalPlaceId;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static Place kakao(String kakaoPlaceId, String name, String address, double latitude, double longitude) {
        return new Place(PROVIDER_KAKAO, kakaoPlaceId, name, address, scale(latitude), scale(longitude));
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP);
    }
}
