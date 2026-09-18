package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.CreditLimit;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.port.out.CreditLimitRepositoryPort;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CreditLimitApplicationService {

    private final CreditLimitRepositoryPort creditLimitRepository;
    private final MemberRepositoryPort memberRepository;
    private final ObligationRepositoryPort obligationRepository;

    public CreditLimitApplicationService(
            CreditLimitRepositoryPort creditLimitRepository,
            MemberRepositoryPort memberRepository,
            ObligationRepositoryPort obligationRepository) {
        this.creditLimitRepository = creditLimitRepository;
        this.memberRepository = memberRepository;
        this.obligationRepository = obligationRepository;
    }

    @Transactional(readOnly = true)
    public List<CreditLimit> listLimits() {
        return creditLimitRepository.findAll();
    }

    /**
     * 设置（新建或调整）会员在某币种上的额度上限。
     */
    @Transactional
    public CreditLimit setLimit(String memberId, String currency, BigDecimal limitAmount) {
        if (currency == null || currency.isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "currency is required");
        }
        if (limitAmount == null || limitAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("VALIDATION_ERROR", "limitAmount must be zero or positive");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "member not found: " + memberId));
        String ccy = currency.trim().toUpperCase();
        CreditLimit limit = creditLimitRepository.findByMemberAndCurrency(member.getMemberId(), ccy)
                .map(existing -> {
                    existing.updateLimit(limitAmount);
                    return existing;
                })
                .orElseGet(() -> CreditLimit.create(member.getMemberId(), ccy, limitAmount));
        return creditLimitRepository.save(limit);
    }

    @Transactional
    public void removeLimit(String memberId, String currency) {
        if (currency == null || currency.isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "currency is required");
        }
        creditLimitRepository.deleteByMemberAndCurrency(memberId, currency.trim().toUpperCase());
    }

    /**
     * 会员在某币种上的额度使用情况：已配置上限（未配置为 null）与 OPEN 义务占用合计。
     */
    @Transactional(readOnly = true)
    public LimitUsage getUsage(String memberId, String currency) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "member not found: " + memberId));
        String ccy = currency.trim().toUpperCase();
        BigDecimal limitAmount = creditLimitRepository.findByMemberAndCurrency(memberId, ccy)
                .map(CreditLimit::getLimitAmount)
                .orElse(null);
        BigDecimal usedAmount = obligationRepository.sumOpenAmountByPayerAndCurrency(memberId, ccy);
        return new LimitUsage(memberId, ccy, limitAmount, usedAmount);
    }

    public record LimitUsage(String memberId, String currency, BigDecimal limitAmount, BigDecimal usedAmount) {
    }
}
