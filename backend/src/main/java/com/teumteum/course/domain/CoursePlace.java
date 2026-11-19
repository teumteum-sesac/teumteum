package com.teumteum.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 특정 코스에 포함된 장소 한 건. course와 place의 N:M 관계를 연결하면서
 * 방문 순서, 체류시간, 저장 당시 장소 정보(스냅샷)를 함께 보관한다.
 * 기본 상세 조회에서는 최신 place 정보 대신 이 엔티티의 저장본을 사용한다.
 * UNIQUE, FK, CHECK 제약조건과 ON DELETE CASCADE는 docs/erd.md의 DDL에서 관리한다.
 */
@Entity
@Table(name = "course_place")
@Getter // 외부에서 값을 읽는 메서드를 생성한다. 저장본을 임의 변경하는 setter는 제공하지 않는다.
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA가 조회 결과로 객체를 만들 때 사용하는 기본 생성자.
public class CoursePlace {

    // 이 저장 장소 행 자체의 PK. 공용 장소의 ID인 placeId와는 다르다.
    // 저장 전에는 null, INSERT 후에는 DB가 AUTO_INCREMENT로 생성한 ID가 들어온다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_place_id")
    private Long coursePlaceId;

    // 여러 CoursePlace가 하나의 Course에 속한다. DB에는 객체 전체가 아니라 course_id가 저장된다.
    // LAZY는 연결된 코스 데이터를 필요할 때 조회하는 방식이다.
    // 코스를 함께 저장/삭제하는 cascade 옵션은 지정하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // 공용 장소를 식별하는 번호. 최신 정보 조회 시 사용하며, 현재는 객체 대신 ID로 참조한다.
    // 자바에서 @ManyToOne으로 연결하지 않아도 DB의 place FK 제약은 적용된다.
    @Column(name = "place_id", nullable = false)
    private Long placeId;

    // 아래 장소 정보는 저장 당시 값의 복사본이다. 공용 place의 이름이 바뀌어도 그대로 유지한다.
    @Column(name = "place_name", nullable = false, length = 200)
    private String placeName;

    // 저장 당시 대분류 코드. 현재는 문자열로 보관하며 허용 값은 DDL의 CHECK로 제한한다.
    // CULTURE / CAFE / FOOD / NATURE / ENTERTAINMENT / SHOPPING / SPORTS / TOURISM
    @Column(name = "category_code", nullable = false, length = 30)
    private String categoryCode;

    // 저장 당시 세분류 이름. 예: 카페, 전시, 공원.
    @Column(name = "sub_category_name", nullable = false, length = 80)
    private String subCategoryName;

    // 선택 정보는 null을 허용한다. @Column의 nullable 기본값은 true다.
    @Column(name = "address", length = 500)
    private String address;

    // DECIMAL(10,7)에 맞춰 전체 10자리, 소수점 이하 7자리로 매핑한다.
    // 위도/경도는 필수이며 유효 범위는 DB의 CHECK 제약으로도 검사한다.
    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    // 긴 소개/운영시간 문자열은 기존 DDL의 TEXT에 맞춘다.
    // columnDefinition은 SQL 타입 정의이며 자바에서 문자열 내용을 검사하는 기능은 아니다.
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "opening_hours", columnDefinition = "TEXT")
    private String openingHours;

    // SMALLINT에 대응하는 Short. 방문 순서는 1~4이며 같은 코스 안에서는 중복할 수 없다.
    // (course_id, visit_order) 중복 방지는 DDL의 UNIQUE 제약이 담당한다.
    @Column(name = "visit_order", nullable = false)
    private Short visitOrder;

    // 이 장소에서 머무르는 예상 시간(분).
    @Column(name = "stay_minutes", nullable = false)
    private Short stayMinutes;

    // 직전 지점에서 이 장소까지 이동시간(분). 첫 장소라면 출발지에서 오는 시간이다.
    // DB의 DEFAULT 0과 별개로 새 자바 객체에서도 0으로 초기화한다.
    @Column(name = "travel_minutes", nullable = false)
    private Short travelMinutes = 0;

    // 선택적인 대중교통 설명. 예: "버스 721 · 환승 0회".
    @Column(name = "transit_info", length = 200)
    private String transitInfo;

    /**
     * 소속 코스와 저장 당시 장소 정보를 받아 새 엔티티를 만든다.
     * Service에서 호출하며, 실제 DB 저장은 Repository의 save() 호출로 진행한다.
     * 장소별로 이 메서드를 호출하므로 장소가 3곳이면 CoursePlace 객체도 3개를 만든다.
     */
    public static CoursePlace create(
            Course course,
            Long placeId,
            String placeName,
            String categoryCode,
            String subCategoryName,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String imageUrl,
            String description,
            String openingHours,
            Short visitOrder,
            Short stayMinutes,
            Short travelMinutes,
            String transitInfo
    ) {
        // 매번 새로운 저장 장소 객체를 만든다. 아직 DB에 저장된 상태는 아니다.
        CoursePlace coursePlace = new CoursePlace();

        // 이 장소가 속한 저장 코스를 연결한다. DB에는 연결된 Course의 ID가 들어간다.
        coursePlace.course = course;
        // 공용 장소 ID는 전달받는다. DB가 자동 생성하는 coursePlaceId와는 다르다.
        coursePlace.placeId = placeId;

        // 왼쪽은 @Column이 붙은 객체의 필드, 오른쪽은 매개변수로 전달받은 값이다.
        // 추천 당시 정보를 복사해 두므로 나중에 공용 장소 정보가 바뀌어도 저장본은 유지된다.
        coursePlace.placeName = placeName;
        coursePlace.categoryCode = categoryCode;
        coursePlace.subCategoryName = subCategoryName;
        coursePlace.address = address;
        coursePlace.latitude = latitude;
        coursePlace.longitude = longitude;
        coursePlace.imageUrl = imageUrl;
        coursePlace.description = description;
        coursePlace.openingHours = openingHours;

        // 방문 순서와 계산된 체류·이동시간도 그대로 복사한다. 여기서 재계산하지 않는다.
        coursePlace.visitOrder = visitOrder;
        coursePlace.stayMinutes = stayMinutes;
        coursePlace.travelMinutes = travelMinutes;
        coursePlace.transitInfo = transitInfo;

        // coursePlaceId는 null로 둔다. INSERT 시 DB가 생성하고 JPA가 객체에 채워 준다.
        return coursePlace;
    }
}
