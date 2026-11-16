package com.teumteum.course.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.teumteum.course.domain.Course;
import com.teumteum.course.domain.CoursePlace;
import com.teumteum.course.domain.CourseRouteLeg;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

class CourseRepositoryBootstrapTest {

    @Test
    @DisplayName("코스 Repository 세 개의 엔티티와 파생 조회 메서드를 구성할 수 있다")
    void createsRepositoriesAndDerivedQueries() {
        // 실제 DB에 접속하지 않고 Repository 생성 시 엔티티 타입과 쿼리 속성 경로를 검증한다.
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();

        try (var sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(Course.class)
                .addAnnotatedClass(CoursePlace.class)
                .addAnnotatedClass(CourseRouteLeg.class)
                .buildMetadata()
                .buildSessionFactory();
                var entityManager = sessionFactory.createEntityManager()) {
            var factory = new JpaRepositoryFactory(entityManager);

            assertDoesNotThrow(() -> factory.getRepository(CourseRepository.class));
            assertDoesNotThrow(() -> factory.getRepository(CoursePlaceRepository.class));
            assertDoesNotThrow(() -> factory.getRepository(CourseRouteLegRepository.class));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
