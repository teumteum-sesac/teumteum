package com.teumteum.course.service;

import com.teumteum.course.dto.CourseSaveResponse;
import com.teumteum.course.repository.*;
import com.teumteum.member.service.MemberService;
import com.teumteum.place.dto.PlaceRegisterCommand;
import com.teumteum.place.service.PlaceService;
import com.teumteum.recommend.dto.RecommendCourseDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 별도 Bean을 통해 트랜잭션을 시작한다. 유니크 충돌은 이 트랜잭션을 롤백한 뒤 호출자가 처리한다.
@Service
@RequiredArgsConstructor
public class CourseSaveWriter {
    private final CourseRepository courses;
    private final CoursePlaceRepository places;
    private final CourseRouteLegRepository routes;
    private final PlaceService placeService;
    private final MemberService memberService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CourseSaveResponse save(Long memberId, RecommendCourseDetailResponse detail) {
        // 같은 회원의 저장과 탈퇴가 동시에 진행돼 탈퇴 뒤 코스가 남지 않도록 잠근다.
        memberService.lockActiveMember(memberId);
        var existing = courses.findByMemberIdAndSourceRecommendationId(memberId, detail.recommendationId());
        if (existing.isPresent()) return CourseSnapshotMapper.response(existing.get(), false);

        CourseSnapshotMapper.validate(detail, detail.recommendationId());
        var course = CourseSnapshotMapper.course(memberId, detail);
        courses.saveAndFlush(course); // ID·저장 시각 생성 및 DB 중복 제약 확인

        // 카카오 ID를 내부 ID로 변환한다. 장소 도메인의 등록은 별도 트랜잭션 계약을 따른다.
        var commands = detail.places().stream().map(p -> new PlaceRegisterCommand(p.placeId(), p.placeName(),
                p.category(), p.subCategory(), p.address(), p.latitude(), p.longitude())).toList();
        var ids = placeService.findOrCreateAll(commands);
        var savedPlaces = detail.places().stream()
                .map(p -> CourseSnapshotMapper.place(course, ids.get(p.placeId()), p)).toList();
        places.saveAllAndFlush(savedPlaces);
        routes.saveAllAndFlush(CourseSnapshotMapper.routes(course, detail.routeLegs()));
        // 자식 저장에 실패하면 코스·장소 스냅샷·경로 모두 롤백된다.
        return CourseSnapshotMapper.response(course, true);
    }
}
