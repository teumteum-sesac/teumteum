package com.teumteum.course.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.descriptor.java.JavaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CourseEntityMappingTest {

    @Test
    @DisplayName("MySQL용 엔티티 매핑을 구성하고 JSON 좌표 목록을 왕복 변환한다")
    @SuppressWarnings("unchecked")
    void bootstrapsMappingsAndRoundTripsRouteCoordinates() throws NoSuchFieldException {
        // 실제 DB 연결이나 테이블 생성 없이 연관관계와 JSON 변환기 구성을 검증한다.
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();

        try (var factory = new MetadataSources(registry)
                .addAnnotatedClass(Course.class)
                .addAnnotatedClass(CoursePlace.class)
                .addAnnotatedClass(CourseRouteLeg.class)
                .buildMetadata()
                .buildSessionFactory()) {
            assertEquals(3, factory.getMetamodel().getEntities().size());

            var implementor = factory.unwrap(SessionFactoryImplementor.class);
            var coordinatesType = CourseRouteLeg.class.getDeclaredField("coordinates").getGenericType();
            var javaType = (JavaType<List<CourseRouteLeg.Coordinate>>) implementor.getTypeConfiguration()
                    .getJavaTypeRegistry().resolveDescriptor(coordinatesType);
            var mapper = implementor.getSessionFactoryOptions().getJsonFormatMapper();
            var options = implementor.getWrapperOptions();
            var coordinates = List.of(
                    new CourseRouteLeg.Coordinate(new BigDecimal("37.544"), new BigDecimal("127.055")),
                    new CourseRouteLeg.Coordinate(new BigDecimal("37.5475"), new BigDecimal("127.061")));

            String json = mapper.toString(coordinates, javaType, options);
            assertTrue(json.startsWith("["));
            assertTrue(json.contains("\"latitude\""));
            assertTrue(json.contains("\"longitude\""));
            assertEquals(coordinates, mapper.fromString(json, javaType, options));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
