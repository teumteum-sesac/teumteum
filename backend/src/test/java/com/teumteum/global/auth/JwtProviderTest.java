package com.teumteum.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * [이 파일이 하는 일]
 * JwtProvider 가 토큰을 만들고, 진짜 토큰에서만 회원 ID 를 꺼내는지 확인한다.
 * 위조(다른 비밀키)·만료·엉터리 토큰은 401 로 거절해야 한다.
 *
 * 실행 (backend/ 에서): gradlew.bat test --tests "com.teumteum.global.auth.JwtProviderTest"
 */
class JwtProviderTest {

    // 테스트용 비밀키 (32자 이상). 실제 키는 application-local.yml 에만 있다
    private static final String SECRET = "test-secret-key-for-teumteum-jwt-0123456789";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, 3600);

    @Test
    @DisplayName("발급한 토큰에서 같은 회원 ID 를 꺼낼 수 있다")
    void createAndParse() {
        String token = jwtProvider.createAccessToken(7L);

        assertThat(token.split("\\.")).hasSize(3);   // 헤더.내용.서명 3덩어리
        assertThat(jwtProvider.getMemberId(token)).isEqualTo(7L);
    }

    @Test
    @DisplayName("다른 비밀키로 만든 토큰(위조)은 401")
    void forgedToken() {
        JwtProvider other = new JwtProvider("another-secret-key-for-forgery-0123456789", 3600);
        String forged = other.createAccessToken(7L);

        assertThatThrownBy(() -> jwtProvider.getMemberId(forged))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("유효 시간이 지난 토큰은 401")
    void expiredToken() {
        // 유효 시간을 -1초로 만들면 발급 순간 이미 만료된 토큰이 된다
        JwtProvider expiredProvider = new JwtProvider(SECRET, -1);
        String expired = expiredProvider.createAccessToken(7L);

        assertThatThrownBy(() -> jwtProvider.getMemberId(expired))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("형식이 엉터리인 토큰은 401")
    void malformedToken() {
        assertThatThrownBy(() -> jwtProvider.getMemberId("not-a-jwt"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);
    }
}
