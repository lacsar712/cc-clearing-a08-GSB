package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.CreditLimit;

import java.util.List;
import java.util.Optional;

public interface CreditLimitRepositoryPort {
    CreditLimit save(CreditLimit creditLimit);

    Optional<CreditLimit> findByMemberAndCurrency(String memberId, String currency);

    List<CreditLimit> findAll();

    List<CreditLimit> findByMemberIds(Iterable<String> memberIds);

    void deleteByMemberAndCurrency(String memberId, String currency);
}
