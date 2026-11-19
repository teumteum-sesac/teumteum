package com.teumteum.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/*
 * [이 파일이 하는 일]
 * Member 엔티티의 메서드(create, changeNickname, withdraw …)가 의도대로 값을 바꾸는지 확인하는 단위 테스트.
 * DB 없이 자바 객체만 만들어서 검사하므로 빠르고, MySQL 이 꺼져 있어도 돈다.
 *
 * 실행: backend 폴더에서  gradlew.bat test --tests "com.teumteum.member.domain.MemberTest"
 *
 * [테스트 읽는 법] given(준비) → when(실행) → then(확인)
 *   assertThat(실제값).isEqualTo(기대값) : 같으면 통과, 다르면 실패
 */
class MemberTest {

    @Test
    @DisplayName("회원을 만들면 입력한 값이 들어가고 상태는 ACTIVE 다")
    void create() {
        // when
        Member member = Member.create("user@email.com", "hashed-password", "틈틈이");

        // then
        assertThat(member.getEmail()).isEqualTo("user@email.com");
        assertThat(member.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(member.getNickname()).isEqualTo("틈틈이");
        assertThat(member.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(member.isActive()).isTrue();
    }

    @Test
    @DisplayName("닉네임을 바꿀 수 있다")
    void changeNickname() {
        // given
        Member member = Member.create("user@email.com", "hashed-password", "틈틈이");

        // when
        member.changeNickname("새닉네임");

        // then
        assertThat(member.getNickname()).isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("비밀번호(해시값)를 바꿀 수 있다")
    void changePassword() {
        // given
        Member member = Member.create("user@email.com", "hashed-password", "틈틈이");

        // when
        member.changePassword("new-hashed-password");

        // then
        assertThat(member.getPasswordHash()).isEqualTo("new-hashed-password");
    }

    @Test
    @DisplayName("탈퇴하면 상태가 WITHDRAWN 이 되고, 같은 이메일로 다시 가입할 수 있게 이메일이 비식별화된다")
    void withdraw() {
        // given
        Member member = Member.create("user@email.com", "hashed-password", "틈틈이");
        org.springframework.test.util.ReflectionTestUtils.setField(member, "id", 5L);

        // when
        member.withdraw();

        // then
        assertThat(member.getAccountStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(member.isActive()).isFalse();
        assertThat(member.getEmail()).isEqualTo("withdrawn+5@teumteum.invalid");
    }
}
