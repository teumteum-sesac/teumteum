package com.teumteum.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 저장한 코스의 지도 경로 한 구간. 경로를 계산하는 클래스가 아니라 계산된 경로의 저장본이다.
 * 예: 출발지 → 카페 → 공원이면 출발지 → 카페, 카페 → 공원을 각각 한 행으로 저장한다.
 * FK, CHECK, 삭제 시 CASCADE 등 실제 DB 제약조건은 docs/erd.md의 DDL을 기준으로 한다.
 */
@Entity
@Table(
    name = "course_route_leg",
    // 같은 코스에서 같은 출발 순서를 가진 구간은 하나만 저장한다.
    // 자바 필드명이 아니라 실제 DB 컬럼명을 지정한다. 각 컬럼을 따로 UNIQUE로 만드는 것은 아니다.
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UK_course_route_leg_order",
            columnNames = {"course_id", "from_visit_order"}
        )
    }
)
@Getter // 조회용 getter만 생성한다. 저장 당시 경로를 임의 변경하는 setter는 제공하지 않는다.
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA가 조회 결과로 엔티티를 만들 때 사용할 기본 생성자.
public class CourseRouteLeg {

    // 이 구간 행의 PK. DB의 AUTO_INCREMENT가 값을 만들고 JPA가 생성된 ID를 받아온다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_route_leg_id")
    private Long courseRouteLegId;

    // 여러 구간이 한 코스에 속하는 N:1 관계. 코스 객체를 참조하지만 DB에는 course_id만 저장한다.
    // 객체 연관관계에는 @Column 대신 @JoinColumn을 사용한다.
    // LAZY는 연결된 코스 데이터를 필요할 때 조회하는 방식이다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // 출발지는 0, 방문 장소는 1~N이다. SMALLINT에 맞춰 Short로 매핑한다.
    @Column(name = "from_visit_order", nullable = false)
    private Short fromVisitOrder;

    // 다음 지점의 순서. 도착지가 있으면 N+1로 표현한다.
    // DDL의 CHECK는 to_visit_order = from_visit_order + 1을 검사한다.
    @Column(name = "to_visit_order", nullable = false)
    private Short toVisitOrder;

    // enum 이름을 문자열로 저장하며, 기존 DDL에 맞춰 VARCHAR(10)으로 정의한다.
    // 경로의 실제/직선 여부이며 Course.estimated의 이동시간 추정 여부와는 별개다.
    @Enumerated(EnumType.STRING)
    @Column(name = "path_type", nullable = false, length = 10, columnDefinition = "VARCHAR(10)")
    private PathType pathType;

    // 진행 방향 순서대로 나열한 좌표 목록 전체를 JSON 컬럼 하나에 저장한다.
    // 예: [{"latitude":37.544,"longitude":127.055},{"latitude":37.5475,"longitude":127.061}]
    // @JdbcTypeCode는 Hibernate가 List와 JSON 사이를 변환하도록 지정한다.
    // @Column(columnDefinition="JSON")만으로는 자바 값의 JSON 변환 방식까지 지정되지 않는다.
    // DDL은 배열과 최소 2개 좌표를 검사한다. 좌표별 필수 값과 범위는 저장 로직에서도 검증해야 한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "coordinates", nullable = false, columnDefinition = "JSON")
    private List<Coordinate> coordinates;

    public enum PathType {
        ACTUAL, // 차량/도보 등 실제 경로의 좌표들.
        STRAIGHT // 지점 사이 직선을 그릴 좌표들.
    }

    // JSON 배열의 원소 한 개. record는 값 보관용 타입으로 생성자와 접근자 등을 자동 제공한다.
    // 엔티티가 아니므로 @Entity, @Id가 필요 없고 별도 테이블도 만들지 않는다.
    // BigDecimal로 십진 좌표를 표현한다. JSON 내부 값이므로 @Column의 precision/scale은 적용되지 않는다.
    public record Coordinate(BigDecimal latitude, BigDecimal longitude) {
    }
}
