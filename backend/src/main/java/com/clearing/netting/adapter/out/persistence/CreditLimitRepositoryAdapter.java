package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.repo.CreditLimitJpaRepository;
import com.clearing.netting.domain.model.CreditLimit;
import com.clearing.netting.domain.port.out.CreditLimitRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CreditLimitRepositoryAdapter implements CreditLimitRepositoryPort {

    private final CreditLimitJpaRepository repository;

    public CreditLimitRepositoryAdapter(CreditLimitJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public CreditLimit save(CreditLimit creditLimit) {
        return PersistenceMapper.toDomain(repository.save(PersistenceMapper.toEntity(creditLimit)));
    }

    @Override
    public Optional<CreditLimit> findByMemberAndCurrency(String memberId, String currency) {
        return repository.findByMemberIdAndCurrency(memberId, currency.toUpperCase())
                .map(PersistenceMapper::toDomain);
    }

    @Override
    public List<CreditLimit> findAll() {
        return repository.findAll().stream().map(PersistenceMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<CreditLimit> findByMemberIds(Iterable<String> memberIds) {
        List<String> ids = new java.util.ArrayList<>();
        memberIds.forEach(ids::add);
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findByMemberIdIn(ids).stream()
                .map(PersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteByMemberAndCurrency(String memberId, String currency) {
        repository.deleteByMemberIdAndCurrency(memberId, currency.toUpperCase());
    }
}
