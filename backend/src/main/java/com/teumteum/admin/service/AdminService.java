package com.teumteum.admin.service;

import com.teumteum.admin.repository.AdminRepository;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * [이 파일이 하는 일]
 * "이 회원이 관리자인가"를 알려 주는 창구 (REQ-044).
 *   - MemberService.login 이 토큰을 만들 때 관리자 표시를 넣으려고 부른다
 *   - 회원 목록에서 관리자 회원을 표시할 때 부른다
 * 다른 도메인은 AdminRepository 를 직접 쓰지 않고 이 서비스를 통한다 (AGENTS.md 5장 도메인 경계).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final AdminRepository adminRepository;

    public boolean isAdmin(Long memberId) {
        return memberId != null && adminRepository.existsById(memberId);
    }

    /** 주어진 회원들 중 관리자인 회원 ID 만 */
    public Set<Long> findAdminIds(Collection<Long> memberIds) {
        return adminRepository.findAllById(memberIds).stream()
                .map(admin -> admin.getMemberId())
                .collect(Collectors.toSet());
    }
}
