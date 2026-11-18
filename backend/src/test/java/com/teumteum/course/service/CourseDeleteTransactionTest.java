package com.teumteum.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import com.teumteum.member.service.MemberWithdrawalService;
import com.teumteum.member.dto.MemberWithdrawRequest;
import com.teumteum.global.auth.JwtProvider;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 실제 Spring 객체와 로컬 DB 설정을 사용한다.
// Hibernate가 테이블 구조를 자동 생성·변경·삭제하지 않도록 설정한다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
@ActiveProfiles("local")

// 실제 DB를 사용하는 테스트라 명시적으로 허용한 경우에만 실행한다.
@EnabledIfEnvironmentVariable(
        named = "TEUMTEUM_MYSQL_TESTS",
        matches = "true"
)
class CourseDeleteTransactionTest {

    @Autowired
    private MemberWithdrawalService withdrawalService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void withdrawalHttpRequestRevokesExistingTokenForMemberAndCourseApis() throws Exception {
        String authorization = "Bearer " + jwtProvider.createAccessToken(fixture.memberId());
        mockMvc.perform(delete("/api/members/me").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"agreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawn").value(true));
        mockMvc.perform(get("/api/members/me").header("Authorization", authorization))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/courses").header("Authorization", authorization))
                .andExpect(status().isUnauthorized());
        transactionTemplate.executeWithoutResult(status -> {
            assertSavedRowCounts(0L);
            assertSharedPlaceExists();
        });
    }

    @Test
    void withdrawalLeavesAnotherMembersCoursesUntouched() {
        transactionTemplate.executeWithoutResult(status -> {
            Fixture other = createFixture();
            withdrawalService.withdrawMyAccount(fixture.memberId(), new MemberWithdrawRequest(null, true));
            entityManager.flush();
            assertSavedRowCounts(0L);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course WHERE course_id = ?",
                    Long.class, other.courseId())).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_place WHERE course_id = ?",
                    Long.class, other.courseId())).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_route_leg WHERE course_id = ?",
                    Long.class, other.courseId())).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM member WHERE member_id = ?",
                    String.class, other.memberId())).isEqualTo("ACTIVE");
            // 추가로 만든 회원·코스는 이 테스트의 트랜잭션과 함께 되돌린다.
            status.setRollbackOnly();
        });
    }

    @Test
    void withdrawalRemovesCoursesAndChildrenButKeepsSharedPlace() {
        withdrawalService.withdrawMyAccount(fixture.memberId(), new MemberWithdrawRequest(null, true));
        transactionTemplate.executeWithoutResult(status -> {
            assertSavedRowCounts(0L);
            assertSharedPlaceExists();
            assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM member WHERE member_id = ?",
                    String.class, fixture.memberId())).isEqualTo("WITHDRAWN");
        });
    }

    @Test
    void withdrawalRollsBackStatusAndCourseDeletionTogether() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            withdrawalService.withdrawMyAccount(fixture.memberId(), new MemberWithdrawRequest(null, true));
            entityManager.flush();
            assertSavedRowCounts(0L);
            assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM member WHERE member_id = ?",
                    String.class, fixture.memberId())).isEqualTo("WITHDRAWN");
            throw new ForcedRollbackException();
        })).isInstanceOf(ForcedRollbackException.class);

        transactionTemplate.executeWithoutResult(status -> {
            assertSavedRowCounts(1L);
            assertSharedPlaceExists();
            assertThat(jdbcTemplate.queryForObject("SELECT account_status FROM member WHERE member_id = ?",
                    String.class, fixture.memberId())).isEqualTo("ACTIVE");
        });
    }

    @Test
    void restoresCourseAndChildrenWhenDeleteTransactionFails() {
        // 준비 데이터를 커밋한 뒤, 별도 트랜잭션에서 삭제와 강제 실패를 실행한다.
        // 실제 Service의 @Transactional은 기본 REQUIRED이므로 이 트랜잭션에 참여한다.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            assertSavedRowCounts(1L);

            var response = courseService.deleteCourse(fixture.memberId(), fixture.courseId());
            assertThat(response.deleted()).isTrue();
            assertThat(response.courseId()).isEqualTo(Long.toString(fixture.courseId()));

            // JPA는 DELETE를 커밋까지 미룰 수 있다. flush로 SQL을 실제 실행한다.
            // 이 시점에도 아직 커밋하지 않았으므로 실패하면 되돌릴 수 있다.
            entityManager.flush();
            assertSavedRowCounts(0L);
            assertSharedPlaceExists();

            // 삭제가 실행된 뒤에만 이 예외에 도달한다. 테스트 전용 예외이며 실제 Service는 수정하지 않는다.
            throw new ForcedRollbackException();
        })).isInstanceOf(ForcedRollbackException.class);

        // 위 트랜잭션이 롤백된 후 새 트랜잭션에서 실제 DB를 다시 읽는다.
        // 영속성 컨텍스트의 객체 대신 SQL COUNT로 코스와 CASCADE 하위 데이터 복구를 확인한다.
        transactionTemplate.executeWithoutResult(status -> {
            assertSavedRowCounts(1L);
            assertSharedPlaceExists();
        });
    }

    private void assertSavedRowCounts(long expected) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course WHERE course_id = ?", Long.class, fixture.courseId()
        )).as("저장 코스 수").isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_place WHERE course_id = ?", Long.class, fixture.courseId()
        )).as("저장 장소 수").isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_route_leg WHERE course_id = ?", Long.class, fixture.courseId()
        )).as("저장 경로 수").isEqualTo(expected);
    }

    private void assertSharedPlaceExists() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM place WHERE place_id = ?", Long.class, fixture.placeId()
        )).as("공용 장소는 삭제하지 않는다").isEqualTo(1L);
    }

    // 다른 DB 오류나 검증 오류를 의도한 롤백으로 오인하지 않도록 별도 예외 타입을 사용한다.
    private static class ForcedRollbackException extends RuntimeException {
    }

    // Mockito 가짜 객체가 아니라 Spring이 만든 실제 Service다.
    @Autowired
    private CourseService courseService;

    // 테스트 데이터 생성과 DB 결과 확인에 사용한다.
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 테스트에서 트랜잭션의 시작·커밋·롤백을 제어할 때 사용한다.
    @Autowired
    private PlatformTransactionManager transactionManager;

    // JPA가 미뤄 둔 삭제 SQL을 flush()로 실제 DB에 실행할 때 사용한다.
    @Autowired
    private EntityManager entityManager;

    // INSERT를 실행하고 AUTO_INCREMENT로 생성된 ID를 반환한다.
    private long insertAndGetId(String sql, Object... values) {
        // DB가 생성한 ID를 받을 객체다.
        var keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            // INSERT 실행 후 생성된 키를 돌려달라고 요청한다.
            var statement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );

            // SQL의 ? 자리에 값을 순서대로 넣는다.
            // JDBC의 매개변수 번호는 0이 아니라 1부터 시작한다.
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }

            return statement;
        }, keyHolder);

        // ID가 반환되지 않았다면 테스트를 중단한다.
        return Objects.requireNonNull(
                keyHolder.getKey(),
                "테스트 데이터의 생성 ID를 받지 못했습니다."
        ).longValue();
    }

    // 테스트에서 직접 만든 데이터의 ID들을 함께 보관한다.
    // 나중에 조회 검증과 테스트 데이터 정리에 사용한다.
    private record Fixture(
            long memberId,
            long placeId,
            long courseId
    ) {
    }

    // 테스트 전용 데이터를 만들고 생성된 ID들을 반환한다.
    // 하나의 트랜잭션 안에서 호출한다.
    private Fixture createFixture() {
        // 반복 실행해도 이메일·외부 장소 ID·추천 ID가 중복되지 않게 한다.
        String uniqueId = UUID.randomUUID().toString();

        // 테스트 회원도 비밀번호 원문 대신 BCrypt 해시를 저장한다.
        String passwordHash = new BCryptPasswordEncoder().encode(uniqueId);

        long memberId = insertAndGetId("""
            INSERT INTO member (email, password_hash, nickname)
            VALUES (?, ?, ?)
            """,
                "delete-test-" + uniqueId + "@example.com",
                passwordHash,
                "삭제테스트"
        );

        // 실제 API를 호출하지 않는 테스트용 공용 장소다.
        long placeId = insertAndGetId("""
            INSERT INTO place (
                place_provider, external_place_id,
                place_name, latitude, longitude
            )
            VALUES ('KAKAO', ?, '[테스트] 롤백 카페', 37.5665, 126.9780)
            """,
                "delete-test-" + uniqueId
        );

        // 총 시간 60분 = 이동 10분 + 체류 50분이다.
        long courseId = insertAndGetId("""
            INSERT INTO course (
                member_id, source_recommendation_id, course_name,
                origin_latitude, origin_longitude,
                transport_type, start_time,
                total_minutes, total_travel_minutes,
                final_travel_minutes, estimated
            )
            VALUES (
                ?, ?, '[테스트] 삭제 롤백',
                37.5650, 126.9770,
                'WALK', '13:00:00',
                60, 10, 0, TRUE
            )
            """,
                memberId,
                "delete-test-" + uniqueId
        );

        // 코스에 포함된 저장 장소 한 개를 만든다.
        jdbcTemplate.update("""
            INSERT INTO course_place (
                course_id, place_id, place_name,
                category_code, sub_category_name,
                latitude, longitude,
                visit_order, stay_minutes, travel_minutes
            )
            VALUES (
                ?, ?, '[테스트] 롤백 카페',
                'CAFE', '카페',
                37.5665, 126.9780,
                1, 50, 10
            )
            """,
                courseId,
                placeId
        );

        // 코스에 포함된 저장 경로 한 개를 만든다.
        jdbcTemplate.update("""
            INSERT INTO course_route_leg (
                course_id, from_visit_order, to_visit_order,
                path_type, coordinates
            )
            VALUES (
                ?, 0, 1, 'STRAIGHT',
                JSON_ARRAY(
                    JSON_OBJECT('latitude', 37.5650, 'longitude', 126.9770),
                    JSON_OBJECT('latitude', 37.5665, 'longitude', 126.9780)
                )
            )
            """,
                courseId
        );

        return new Fixture(memberId, placeId, courseId);
    }

    // 테스트마다 생성한 데이터의 ID를 기억한다.
    private Fixture fixture;

    // 테스트에서 트랜잭션을 직접 실행하는 도구다.
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        // 준비 데이터를 하나의 트랜잭션에서 생성하고 커밋한다.
        // 이후 삭제 트랜잭션을 롤백했을 때 이 데이터가 복구되는지 확인한다.
        fixture = transactionTemplate.execute(status -> createFixture());
    }

    @AfterEach
    void cleanUp() {
        // 준비 단계가 실패해 데이터가 생성되지 않았다면 정리할 것이 없다.
        if (fixture == null) {
            return;
        }

        // 테스트가 성공하든 실패하든, 직접 생성한 데이터만 정리한다.
        transactionTemplate.executeWithoutResult(status -> {
            // 하위 데이터를 먼저 삭제해 FK 연결 순서를 지킨다.
            jdbcTemplate.update(
                    "DELETE FROM course_route_leg WHERE course_id = ?",
                    fixture.courseId()
            );

            jdbcTemplate.update(
                    "DELETE FROM course_place WHERE course_id = ?",
                    fixture.courseId()
            );

            jdbcTemplate.update(
                    "DELETE FROM course WHERE course_id = ?",
                    fixture.courseId()
            );

            jdbcTemplate.update(
                    "DELETE FROM place WHERE place_id = ?",
                    fixture.placeId()
            );

            jdbcTemplate.update(
                    "DELETE FROM member WHERE member_id = ?",
                    fixture.memberId()
            );
        });
    }
}
