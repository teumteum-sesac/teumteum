package com.teumteum.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 저장한 코스 한 건. docs/erd.md의 course 테이블에 대응한다.
 * 장소별 스냅샷과 지도 경로는 각각 course_place, course_route_leg에서 관리한다.
 * 테이블 생성과 UNIQUE, FK, CHECK 제약조건 관리는 기존 DDL을 기준으로 한다.
 */
@Entity // 이 클래스를 JPA가 관리하는 엔티티로 등록한다.
@Table(name = "course") // 자바 클래스 이름과 연결할 실제 테이블 이름을 지정한다.
@Getter // Lombok이 조회용 getter를 생성한다. 저장본을 임의 변경하지 않도록 setter는 제공하지 않는다.
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA가 DB 조회 결과로 객체를 만들 때 쓸 기본 생성자.
public class Course {

    // PK. 저장 전에는 null이고, INSERT 시 DB의 AUTO_INCREMENT로 번호가 생성된다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long courseId;

    // 저장한 회원의 ID. 지금은 회원 객체 대신 번호로 참조한다.
    // @ManyToOne이 없어도 DB에 정의한 FK 제약은 적용된다.
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 원본 추천의 불변 버전 ID. courseId와 달리 추천 결과에서 받은 값을 저장한다.
    // 같은 회원의 동일 추천 중복 저장은 DDL의 (member_id, source_recommendation_id) UNIQUE가 막는다.
    @Column(name = "source_recommendation_id", nullable = false, length = 128)
    private String sourceRecommendationId;

    // VARCHAR(810) NOT NULL에 대응한다. nullable=false는 빈 문자열 검사를 대신하지 않는다.
    @Column(name = "course_name", nullable = false, length = 810)
    private String courseName;

    // 출발지 이름과 주소는 없어도 되지만, 출발지 좌표는 필수다.
    // nullable을 생략하면 기본값은 true다.
    @Column(name = "origin_name", length = 200)
    private String originName;

    @Column(name = "origin_address", length = 500)
    private String originAddress;

    // DECIMAL(10,7): 전체 숫자 10자리 중 소수점 이하 7자리.
    // BigDecimal은 십진수를 정확히 표현하므로 DB의 DECIMAL과 대응시키기 좋다.
    @Column(name = "origin_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal originLatitude;

    @Column(name = "origin_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal originLongitude;

    // 도착지는 선택 사항이다. 없으면 아래 네 필드는 모두 null, finalTravelMinutes는 0이다.
    @Column(name = "destination_name", length = 200)
    private String destinationName;

    @Column(name = "destination_address", length = 500)
    private String destinationAddress;

    @Column(name = "destination_latitude", precision = 10, scale = 7)
    private BigDecimal destinationLatitude;

    @Column(name = "destination_longitude", precision = 10, scale = 7)
    private BigDecimal destinationLongitude;

    // enum의 순번(0, 1, 2)이 아니라 이름("TRANSIT", "WALK", "CAR")을 저장한다.
    // columnDefinition은 MySQL의 네이티브 ENUM 대신 기존 DDL의 VARCHAR(20)에 맞춘다.
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private TransportType transportType;

    // TIME에는 날짜가 없으므로 LocalTime을 쓴다. 30분 단위 제한은 DDL의 CHECK에 정의되어 있다.
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    // SMALLINT는 16비트 정수여서 Short로 매핑한다. 이동과 체류를 포함한 총 소요시간(분).
    @Column(name = "total_minutes", nullable = false)
    private Short totalMinutes;

    // DB의 DEFAULT 0은 자바 객체의 값을 채워주지 않으므로 자바에서도 초기값을 지정한다.
    @Column(name = "total_travel_minutes", nullable = false)
    private Short totalTravelMinutes = 0;

    // 마지막 장소에서 도착지까지의 이동시간. 도착지가 없으면 0이다.
    @Column(name = "final_travel_minutes", nullable = false)
    private Short finalTravelMinutes = 0;

    // 이동시간 추정 구간이 하나라도 있으면 true. 지도에서 직선을 그리는지와는 별개다.
    // boolean은 null이 없으며 자바 기본값은 false다.
    @Column(name = "estimated", nullable = false)
    private boolean estimated;

    // DATETIME은 날짜와 시각을 함께 담으므로 LocalDateTime으로 매핑한다.
    // 저장 시각은 수정하지 않는다. 시간대 정보 자체는 이 타입과 DB 컬럼에 포함되지 않는다.
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
    private LocalDateTime createdAt;

    // JPA가 새 엔티티를 저장하기 전에 호출한다. 조회할 때마다 실행되는 메서드는 아니다.
    // INSERT에 null을 보내면 DB의 DEFAULT가 적용되지 않으므로 애플리케이션에서 값을 채운다.
    @PrePersist
    protected void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now().withNano(0); // DDL의 DATETIME과 같이 초 단위로 기록한다.
        }
    }

    // 허용된 이동수단만 표현하는 타입. 공통 타입이 정해지면 그 타입으로 통합할 수 있다.
    public enum TransportType {
        TRANSIT, // 대중교통
        WALK, // 도보
        CAR // 차량
    }
}
