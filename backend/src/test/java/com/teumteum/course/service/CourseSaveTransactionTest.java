package com.teumteum.course.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.teumteum.course.repository.CourseRouteLegRepository;
import com.teumteum.global.auth.JwtProvider;
import com.teumteum.global.exception.CustomException;
import com.teumteum.recommend.exception.RecommendErrorCode;
import com.teumteum.recommend.service.RecommendService;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

// 테스트 전용 회원·장소만 생성·정리한다. 외부 API는 호출하지 않는다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "TEUMTEUM_MYSQL_TESTS", matches = "true")
class CourseSaveTransactionTest {
    @Autowired CourseService courses;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @MockitoBean RecommendService recommendations;
    @MockitoSpyBean CourseRouteLegRepository routeRepository;
    long memberId;
    long placeId;
    String externalId;
    String recommendationId;
    Long insertedMainCategoryId;
    Long insertedSubCategoryId;

    @BeforeEach
    void fixtures() {
        String unique = UUID.randomUUID().toString();
        externalId = "save-test-" + unique;
        recommendationId = "rc_" + unique;
        memberId = insert("INSERT INTO member(email,password_hash,nickname) VALUES(?,?,?)",
                unique + "@example.com", new BCryptPasswordEncoder().encode(unique), "저장테스트");
        placeId = insert("INSERT INTO place(place_provider,external_place_id,place_name,latitude,longitude) VALUES('KAKAO',?,?,?,?)",
                externalId, "공용 장소 원본", 37.5665, 126.978);
        given(recommendations.getCourseDetail(recommendationId))
                .willReturn(CourseSaveFixtures.detail(recommendationId, externalId));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM course WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM place_category WHERE place_id IN (SELECT place_id FROM place WHERE place_provider='KAKAO' AND external_place_id=?)", externalId);
        jdbc.update("DELETE FROM place WHERE place_provider='KAKAO' AND external_place_id=?", externalId);
        if (insertedSubCategoryId != null) jdbc.update("DELETE FROM category WHERE category_id=?", insertedSubCategoryId);
        if (insertedMainCategoryId != null) jdbc.update("DELETE FROM category WHERE category_id=?", insertedMainCategoryId);
        jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
    }

    @Test
    void registersUnknownKakaoPlaceAndUsesGeneratedInternalId() {
        jdbc.update("DELETE FROM place WHERE place_id=?", placeId);
        var parents = jdbc.queryForList("SELECT category_id FROM category WHERE category_code='CAFE'", Long.class);
        long parent;
        if (parents.isEmpty()) {
            parent = insert("INSERT INTO category(category_code,category_name,category_level) VALUES('CAFE','카페/디저트',1)");
            insertedMainCategoryId = parent;
        } else {
            parent = parents.getFirst();
        }
        String subCategory = "저장테스트-" + UUID.randomUUID();
        insertedSubCategoryId = insert("INSERT INTO category(parent_category_id,category_name,category_level,stay_minutes) VALUES(?,?,2,50)", parent, subCategory);
        given(recommendations.getCourseDetail(recommendationId))
                .willReturn(CourseSaveFixtures.detail(recommendationId, externalId, subCategory));

        var result = courses.saveCourse(memberId, recommendationId);
        long actualPlaceId = jdbc.queryForObject("SELECT place_id FROM place WHERE place_provider='KAKAO' AND external_place_id=?", Long.class, externalId);
        assertThat(courses.getCourse(memberId, Long.valueOf(result.courseId())).places().getFirst().placeId())
                .isEqualTo(Long.toString(actualPlaceId));
        assertSnapshotCounts(1);
    }

    @Test
    void savesHttpSnapshotAndReusesItAfterRecommendationExpires() throws Exception {
        String auth = "Bearer " + jwt.createAccessToken(memberId);
        mvc.perform(post("/api/courses").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommendationId\":\"" + recommendationId + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.created").value(true));
        var saved = courses.saveCourse(memberId, recommendationId);
        var snapshot = courses.getCourse(memberId, Long.valueOf(saved.courseId()));
        assertThat(saved.created()).isFalse();
        assertThat(snapshot.places().getFirst().placeId()).isEqualTo(Long.toString(placeId));
        assertThat(snapshot.places().getFirst().placeName()).isEqualTo("저장 카페");
        assertThat(snapshot.places().getFirst().description()).isEqualTo("저장 소개");
        assertThat(snapshot.routeLegs()).hasSize(1);
        jdbc.update("UPDATE place SET place_name='공용 장소 변경' WHERE place_id=?", placeId);
        given(recommendations.getCourseDetail(recommendationId))
                .willThrow(new CustomException(RecommendErrorCode.RECOMMENDATION_EXPIRED));
        var again = courses.saveCourse(memberId, recommendationId);
        assertThat(again).isEqualTo(saved);
        assertThat(courses.getCourse(memberId, Long.valueOf(saved.courseId()))).isEqualTo(snapshot);
        verify(recommendations, times(1)).getCourseDetail(recommendationId);
        mvc.perform(post("/api/courses").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recommendationId\":\"" + recommendationId + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(false));
    }

    @Test
    void concurrentRequestsCreateOneCompleteSnapshot() throws Exception {
        var ready = new CountDownLatch(2);
        given(recommendations.getCourseDetail(recommendationId)).willAnswer(invocation -> {
            ready.countDown();
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrency timeout");
            return CourseSaveFixtures.detail(recommendationId, externalId);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> courses.saveCourse(memberId, recommendationId));
            var b = executor.submit(() -> courses.saveCourse(memberId, recommendationId));
            var first = a.get(20, TimeUnit.SECONDS);
            var second = b.get(20, TimeUnit.SECONDS);
            assertThat(first.courseId()).isEqualTo(second.courseId());
            assertThat(first.created()).isNotEqualTo(second.created());
        }
        assertSnapshotCounts(1);
    }

    @Test
    void rollsBackCourseAndPlacesWhenRouteWriteFails() {
        doThrow(new IllegalStateException("forced route failure"))
                .when(routeRepository).saveAllAndFlush(any());
        assertThatThrownBy(() -> courses.saveCourse(memberId, recommendationId))
                .isInstanceOf(IllegalStateException.class);
        assertSnapshotCounts(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place WHERE place_id=?", Long.class, placeId)).isEqualTo(1L);
    }

    @Test
    void rejectsExpiredRecommendationWithoutWritingSnapshot() {
        given(recommendations.getCourseDetail(recommendationId))
                .willThrow(new CustomException(RecommendErrorCode.RECOMMENDATION_EXPIRED));
        assertThatThrownBy(() -> courses.saveCourse(memberId, recommendationId)).isInstanceOf(CustomException.class);
        assertSnapshotCounts(0);
    }

    @Test
    void rejectsSaveForWithdrawnMember() {
        jdbc.update("UPDATE member SET account_status='WITHDRAWN' WHERE member_id=?", memberId);
        assertThatThrownBy(() -> courses.saveCourse(memberId, recommendationId)).isInstanceOf(CustomException.class);
        assertSnapshotCounts(0);
    }

    private void assertSnapshotCounts(long count) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course WHERE member_id=?", Long.class, memberId)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_place p JOIN course c ON c.course_id=p.course_id WHERE c.member_id=?", Long.class, memberId)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM course_route_leg r JOIN course c ON c.course_id=r.course_id WHERE c.member_id=?", Long.class, memberId)).isEqualTo(count);
    }

    private long insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
