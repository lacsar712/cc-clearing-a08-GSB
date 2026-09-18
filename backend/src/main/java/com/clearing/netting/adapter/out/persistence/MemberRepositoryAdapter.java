package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.entity.MemberCreditLimitJpaEntity;
import com.clearing.netting.adapter.out.persistence.entity.MemberCreditLimitId;
import com.clearing.netting.adapter.out.persistence.entity.MemberJpaEntity;
import com.clearing.netting.adapter.out.persistence.repo.MemberCreditLimitJpaRepository;
import com.clearing.netting.adapter.out.persistence.repo.MemberJpaRepository;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class MemberRepositoryAdapter implements MemberRepositoryPort {

    private final MemberJpaRepository repository;
    private final MemberCreditLimitJpaRepository limitRepository;

    public MemberRepositoryAdapter(MemberJpaRepository repository,
                                   MemberCreditLimitJpaRepository limitRepository) {
        this.repository = repository;
        this.limitRepository = limitRepository;
    }

    @Override
    public Member save(Member member) {
        MemberJpaEntity saved = repository.save(PersistenceMapper.toEntity(member));
        syncLimits(member.getMemberId(), member.getCreditLimits());
        return PersistenceMapper.toDomain(saved, limitRepository.findByIdMemberId(member.getMemberId()));
    }

    @Override
    public Optional<Member> findById(String memberId) {
        return repository.findById(memberId)
                .map(e -> PersistenceMapper.toDomain(e, limitRepository.findByIdMemberId(memberId)));
    }

    @Override
    public List<Member> findAll() {
        Map<String, List<MemberCreditLimitJpaEntity>> limitsByMember = limitRepository.findAll().stream()
                .collect(Collectors.groupingBy(l -> l.getId().getMemberId()));
        return repository.findAll().stream()
                .map(e -> PersistenceMapper.toDomain(e,
                        limitsByMember.getOrDefault(e.getMemberId(), List.of())))
                .collect(Collectors.toList());
    }

    @Override
    public List<Member> findByIds(Iterable<String> memberIds) {
        List<String> ids = StreamSupport.stream(memberIds.spliterator(), false).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, List<MemberCreditLimitJpaEntity>> limitsByMember = limitRepository.findAll().stream()
                .filter(l -> ids.contains(l.getId().getMemberId()))
                .collect(Collectors.groupingBy(l -> l.getId().getMemberId()));
        return repository.findAllById(ids).stream()
                .map(e -> PersistenceMapper.toDomain(e,
                        limitsByMember.getOrDefault(e.getMemberId(), List.of())))
                .collect(Collectors.toList());
    }

    private void syncLimits(String memberId, Map<String, BigDecimal> desired) {
        List<MemberCreditLimitJpaEntity> existing = limitRepository.findByIdMemberId(memberId);
        Set<String> desiredCurrencies = desired.keySet();
        for (MemberCreditLimitJpaEntity current : existing) {
            if (!desiredCurrencies.contains(current.getId().getCurrency())) {
                limitRepository.deleteById(current.getId());
            }
        }
        desired.forEach((currency, amount) ->
                limitRepository.save(PersistenceMapper.toLimitEntity(memberId, currency, amount)));
    }
}
