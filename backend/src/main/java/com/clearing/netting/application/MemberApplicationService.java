package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MemberApplicationService {

    private final MemberRepositoryPort memberRepository;
    private final ObligationRepositoryPort obligationRepository;

    public MemberApplicationService(MemberRepositoryPort memberRepository,
                                    ObligationRepositoryPort obligationRepository) {
        this.memberRepository = memberRepository;
        this.obligationRepository = obligationRepository;
    }

    @Transactional(readOnly = true)
    public List<Member> listMembers() {
        return memberRepository.findAll();
    }

    @Transactional
    public Member createMember(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("INVALID_NAME", "member name is required");
        }
        return memberRepository.save(Member.create(name.trim()));
    }

    @Transactional
    public Member updateStatus(String memberId, MemberStatus status) {
        Member member = requireMember(memberId);
        if (status == MemberStatus.ACTIVE) {
            member.activate();
        } else if (status == MemberStatus.SUSPENDED) {
            member.suspend();
        } else {
            throw new DomainException("INVALID_STATUS", "unknown status");
        }
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public Member getMember(String memberId) {
        return requireMember(memberId);
    }

    @Transactional(readOnly = true)
    public List<CreditLimitView> listCreditLimits() {
        Map<String, Map<String, BigDecimal>> used =
                obligationRepository.sumOpenAmountsGroupedByPayerAndCurrency();
        List<CreditLimitView> views = new ArrayList<>();
        for (Member member : memberRepository.findAll()) {
            Map<String, BigDecimal> usedByCurrency = used.getOrDefault(member.getMemberId(), Map.of());
            member.getCreditLimits()
                    .forEach((currency, limit) -> views.add(new CreditLimitView(
                            member.getMemberId(),
                            member.getName(),
                            currency,
                            limit,
                            usedByCurrency.getOrDefault(currency, BigDecimal.ZERO))));
        }
        views.sort((a, b) -> {
            int byMember = a.memberName().compareTo(b.memberName());
            return byMember != 0 ? byMember : a.currency().compareTo(b.currency());
        });
        return views;
    }

    @Transactional
    public CreditLimitView upsertCreditLimit(String memberId, String currency, BigDecimal limitAmount) {
        Member member = requireMember(memberId);
        String ccy = normalizeCurrency(currency);
        if (limitAmount == null || limitAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("INVALID_LIMIT", "credit limit must be positive");
        }
        member.setCreditLimit(ccy, limitAmount);
        memberRepository.save(member);
        BigDecimal used = obligationRepository.sumOpenByPayerAndCurrency(memberId, ccy);
        return new CreditLimitView(member.getMemberId(), member.getName(), ccy,
                member.getCreditLimit(ccy), used);
    }

    @Transactional
    public void removeCreditLimit(String memberId, String currency) {
        Member member = requireMember(memberId);
        member.removeCreditLimit(normalizeCurrency(currency));
        memberRepository.save(member);
    }

    private Member requireMember(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "member not found: " + memberId));
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new DomainException("INVALID_CURRENCY", "currency is required");
        }
        return currency.trim().toUpperCase();
    }
}
