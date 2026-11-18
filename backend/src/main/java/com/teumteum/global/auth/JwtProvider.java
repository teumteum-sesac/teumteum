package com.teumteum.global.auth;

import com.teumteum.global.exception.CommonErrorCode;
import com.teumteum.global.exception.CustomException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
 * [이 파일이 하는 일]
 * 로그인 토큰(JWT)을 만들고(발급), 받은 토큰이 진짜인지 확인해서 회원 ID 를 꺼낸다(검증).
 *
 * [JWT 가 뭔가]
 *   로그인에 성공한 사람에게 주는 "출입증" 문자열. 점(.)으로 나뉜 3덩어리다.
 *     eyJhbGciOi...(헤더) . eyJzdWIiOi...(내용: 회원 ID·만료 시각) . SflKxwRJ...(서명)
 *   - 내용(회원 ID 등)은 누구나 읽을 수 있다 → 비밀번호 같은 민감 정보는 절대 넣지 않는다
 *   - 서명은 서버만 아는 비밀키(jwt.secret)로 만든다 → 누가 내용을 바꾸면 서명이 안 맞아서 가짜로 걸러진다
 *   - 서버가 로그인 상태를 따로 저장하지 않아도 된다 (토큰만 보고 판단 = 무상태, SecurityConfig 의 STATELESS)
 *
 * [흐름]
 *   로그인 성공 → createAccessToken(회원 ID) → 프론트에 토큰 전달 → 프론트가 authStore 에 보관
 *   이후 요청마다 헤더 "Authorization: Bearer 토큰" → getMemberId(토큰) 으로 누구인지 확인
 *   (요청마다 확인하는 필터와 @LoginMember 교체는 다음 작업에서 한다)
 *
 * [설정값] application-local.yml (커밋 안 됨, NFR-005·033)
 *   jwt.secret                         : 서명용 비밀키. 32자(256비트) 이상. 짧으면 서버가 켜질 때 오류
 *   jwt.access-token-validity-seconds  : 토큰 유효 시간(초). 예: 3600 = 1시간
 */
@Component
public class JwtProvider {

    /** 토큰 안에 관리자 표시를 넣는 칸 이름과 값 */
    private static final String ROLE_CLAIM = "role";
    private static final String ROLE_ADMIN = "ADMIN";

    private final SecretKey secretKey;
    private final long validitySeconds;

    /**
     * @Value: application-local.yml 의 값을 읽어 생성자에 넣어 준다.
     * 비밀키 문자열을 HMAC-SHA 서명용 키 객체로 바꿔 둔다 (Keys.hmacShaKeyFor, 너무 짧으면 WeakKeyException).
     */
    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-seconds}") long validitySeconds
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validitySeconds = validitySeconds;
    }

    /**
     * 로그인 토큰 발급.
     *   subject(주인) = 회원 ID, issuedAt = 지금, expiration = 지금 + 유효 시간, 마지막에 비밀키로 서명
     */
    public String createAccessToken(Long memberId) {
        return createAccessToken(memberId, false);
    }

    /**
     * 관리자면 토큰에 role=ADMIN 표시를 넣는다 (REQ-044).
     * 관리자 여부는 로그인할 때 admin 테이블로 확인해 토큰에 담고, 요청마다 DB 를 다시 보지 않는다.
     * → 관리자로 새로 지정·해제된 사람은 다시 로그인해야 반영된다.
     */
    public String createAccessToken(Long memberId, boolean admin) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + validitySeconds * 1000);

        var builder = Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(now)
                .expiration(expiresAt);
        if (admin) {
            builder.claim(ROLE_CLAIM, ROLE_ADMIN);
        }
        return builder.signWith(secretKey).compact();
    }

    public Long getMemberId(String token) {
        return parse(token).memberId();
    }

    /** 토큰 검증 후 회원 ID 와 관리자 여부를 꺼낸다. 위조·만료·형식 오류면 401 */
    public TokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)          // 이 비밀키로 만든 서명인지 확인
                    .build()
                    .parseSignedClaims(token)       // 서명·만료 시각 검사 (실패하면 JwtException)
                    .getPayload();
            return new TokenClaims(Long.valueOf(claims.getSubject()), ROLE_ADMIN.equals(claims.get(ROLE_CLAIM, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED, e);
        }
    }

    /** 토큰에서 꺼낸 값: 회원 ID, 관리자 여부 */
    public record TokenClaims(Long memberId, boolean admin) {
    }

    public long getValiditySeconds() {
        return validitySeconds;
    }
}
