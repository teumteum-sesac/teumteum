package com.teumteum.place.service;

import com.teumteum.global.exception.CustomException;
import com.teumteum.place.domain.Place;
import com.teumteum.place.dto.PlaceRegisterCommand;
import com.teumteum.place.exception.PlaceErrorCode;
import com.teumteum.place.repository.PlaceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PlaceService 전용. 장소 등록을 호출한 쪽(코스 저장 등)과 분리된 새 트랜잭션에서 처리한다.
 *
 * 이유: 두 요청이 같은 장소를 동시에 등록하면 한쪽은 유니크 키 오류가 난다.
 * 같은 트랜잭션 안에서 이 오류가 나면 호출한 쪽 트랜잭션까지 롤백 전용이 되고,
 * 다시 조회해도 MySQL 기본 격리 수준(REPEATABLE READ)에서는 상대가 넣은 행이 안 보인다.
 * 그래서 등록과 재조회를 각각 새 트랜잭션에서 한다.
 */
@Component
@RequiredArgsConstructor
class PlaceRegistrar {

    private final PlaceRepository placeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long findOrInsert(PlaceRegisterCommand command) {
        return placeRepository.findByProviderAndExternalPlaceId(Place.PROVIDER_KAKAO, command.kakaoPlaceId())
                .map(Place::getId)
                .orElseGet(() -> insert(command));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Long> findId(String kakaoPlaceId) {
        return placeRepository.findByProviderAndExternalPlaceId(Place.PROVIDER_KAKAO, kakaoPlaceId)
                .map(Place::getId);
    }

    private Long insert(PlaceRegisterCommand command) {
        Long subCategoryId = placeRepository
                .findSubCategoryId(command.category().name(), command.subCategoryName())
                .orElseThrow(() -> new CustomException(PlaceErrorCode.INVALID_CATEGORY));

        Place saved = placeRepository.saveAndFlush(Place.kakao(
                command.kakaoPlaceId(),
                command.name(),
                command.address(),
                command.latitude(),
                command.longitude()));
        placeRepository.insertPlaceCategory(saved.getId(), subCategoryId);
        return saved.getId();
    }
}
