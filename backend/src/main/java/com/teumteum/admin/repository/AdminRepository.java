package com.teumteum.admin.repository;

import com.teumteum.admin.domain.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

/** 관리자 명단 조회. 기본 키가 회원 ID 라서 existsById(회원ID) 로 관리자인지 바로 확인한다 */
public interface AdminRepository extends JpaRepository<Admin, Long> {
}
