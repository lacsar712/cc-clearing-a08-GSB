package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.CreditLimitJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditLimitJpaRepository extends JpaRepository<CreditLimitJpaEntity, String> {

    Optional<CreditLimitJpaEntity> findByMemberIdAndCurrency(String memberId, String currency);

    List<CreditLimitJpaEntity> findByMemberIdIn(List<String> memberIds);

    void deleteByMemberIdAndCurrency(String memberId, String currency);
}
