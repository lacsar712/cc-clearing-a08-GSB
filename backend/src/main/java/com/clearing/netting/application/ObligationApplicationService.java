package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.CreditLimit;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.port.out.CreditLimitRepositoryPort;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ObligationApplicationService {

    private final ObligationRepositoryPort obligationRepository;
    private final MemberRepositoryPort memberRepository;
    private final CreditLimitRepositoryPort creditLimitRepository;

    public ObligationApplicationService(
            ObligationRepositoryPort obligationRepository,
            MemberRepositoryPort memberRepository,
            CreditLimitRepositoryPort creditLimitRepository) {
        this.obligationRepository = obligationRepository;
        this.memberRepository = memberRepository;
        this.creditLimitRepository = creditLimitRepository;
    }

    @Transactional(readOnly = true)
    public List<TradeObligation> list(String currency, LocalDate settleDate, ObligationStatus status) {
        return obligationRepository.findByFilters(currency, settleDate, status);
    }

    @Transactional
    public TradeObligation create(
            String payerMemberId,
            String payeeMemberId,
            String currency,
            BigDecimal amount,
            LocalDate tradeDate,
            LocalDate settleDate) {
        Member payer = validateMember(payerMemberId);
        validateMember(payeeMemberId);
        String ccy = currency.trim().toUpperCase();
        enforceCreditLimit(payer, ccy, amount);
        TradeObligation obligation = TradeObligation.open(
                payerMemberId, payeeMemberId, ccy, amount, tradeDate, settleDate);
        return obligationRepository.save(obligation);
    }

    /**
     * 仅约束新建义务：付款方在该币种上的 OPEN 义务占用 + 本次金额不得超过额度上限。
     * 未配置额度的币种视为不限额。
     */
    private void enforceCreditLimit(Member payer, String currency, BigDecimal amount) {
        CreditLimit limit = creditLimitRepository
                .findByMemberAndCurrency(payer.getMemberId(), currency)
                .orElse(null);
        if (limit == null) {
            return;
        }
        BigDecimal used = obligationRepository.sumOpenAmountByPayerAndCurrency(payer.getMemberId(), currency);
        if (limit.isExceeded(used, amount)) {
            throw new DomainException(
                    "CREDIT_LIMIT_EXCEEDED",
                    String.format(
                            "会员 %s 在币种 %s 上的额度不足：额度上限 %s，已用（OPEN 义务）%s，本次申请 %s",
                            payer.getName(), currency,
                            limit.getLimitAmount().toPlainString(),
                            used.toPlainString(),
                            amount.toPlainString()));
        }
    }

    private Member validateMember(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "member not found: " + memberId));
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new DomainException("SUSPENDED_MEMBER", "cannot create obligation for suspended member: " + memberId);
        }
        return member;
    }
}
