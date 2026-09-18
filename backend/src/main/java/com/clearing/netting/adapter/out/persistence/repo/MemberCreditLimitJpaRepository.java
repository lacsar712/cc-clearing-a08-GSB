package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.MemberCreditLimitJpaEntity;
import com.clearing.netting.adapter.out.persistence.entity.MemberCreditLimitId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberCreditLimitJpaRepository extends JpaRepository<MemberCreditLimitJpaEntity, MemberCreditLimitId> {

    List<MemberCreditLimitJpaEntity> findByIdMemberId(String memberId);
}
