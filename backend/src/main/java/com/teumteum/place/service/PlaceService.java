package com.teumteum.place.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.dto.PlaceRegisterCommand;
import com.teumteum.place.exception.PlaceErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 공용 장소(place) 조회·등록 (NFR-010).
 * 코스 저장(REQ-030) 등 다른 도메인이 카카오 장소 ID 를 내부 place_id 로 바꿀 때 쓴다.
 */
@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRegistrar placeRegistrar;

    /**
     * 카카오 장소 ID 로 place 를 찾고, 없으면 등록한 뒤 내부 place_id 를 돌려준다.
     * 이미 있는 장소는 기존 정보를 그대로 두고 ID 만 돌려준다 (저장본은 course_place 에 따로 복사).
     *
     * @throws CustomException INVALID_COORDINATE 좌표 범위 오류, INVALID_CATEGORY 세분류를 찾을 수 없음
     */
    public Long findOrCreate(PlaceRegisterCommand command) {
        validate(command);
        try {
            return placeRegistrar.findOrInsert(command);
        } catch (DataIntegrityViolationException e) {
            // 다른 요청이 같은 장소를 먼저 등록함 → 그 장소의 ID 를 돌려준다
            return placeRegistrar.findId(command.kakaoPlaceId()).orElseThrow(() -> e);
        }
    }

    /**
     * 여러 장소를 한 번에 처리한다 (코스 하나에 최대 4곳).
     *
     * @return 카카오 장소 ID → 내부 place_id. 넘긴 순서를 유지하고, 같은 카카오 ID 는 한 번만 처리한다
     */
    public Map<String, Long> findOrCreateAll(List<PlaceRegisterCommand> commands) {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (PlaceRegisterCommand command : commands) {
            if (!ids.containsKey(command.kakaoPlaceId())) {
                ids.put(command.kakaoPlaceId(), findOrCreate(command));
            }
        }
        return ids;
    }

    private void validate(PlaceRegisterCommand command) {
        // 다른 서비스가 호출하는 내부 메서드라, 필수값 누락은 호출 코드의 실수로 본다
        if (command == null
                || !StringUtils.hasText(command.kakaoPlaceId())
                || !StringUtils.hasText(command.name())
                || command.category() == null) {
            throw new IllegalArgumentException("카카오 장소 ID, 장소명, 대분류는 필수입니다.");
        }
        if (!StringUtils.hasText(command.subCategoryName())) {
            throw new CustomException(PlaceErrorCode.INVALID_CATEGORY);
        }
        if (command.latitude() < -90 || command.latitude() > 90
                || command.longitude() < -180 || command.longitude() > 180) {
            throw new CustomException(PlaceErrorCode.INVALID_COORDINATE);
        }
    }
}
