package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
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

    public ObligationApplicationService(
            ObligationRepositoryPort obligationRepository,
            MemberRepositoryPort memberRepository) {
        this.obligationRepository = obligationRepository;
        this.memberRepository = memberRepository;
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
        String ccy = currency == null ? null : currency.trim().toUpperCase();
        checkCreditLimit(payer, ccy, amount);
        TradeObligation obligation = TradeObligation.open(
                payerMemberId, payeeMemberId, currency, amount, tradeDate, settleDate);
        return obligationRepository.save(obligation);
    }

    private Member validateMember(String memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "member not found: " + memberId));
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new DomainException("SUSPENDED_MEMBER", "cannot create obligation for suspended member: " + memberId);
        }
        return member;
    }

    private void checkCreditLimit(Member payer, String currency, BigDecimal amount) {
        BigDecimal limit = payer.getCreditLimit(currency);
        if (limit == null) {
            return;
        }
        BigDecimal used = obligationRepository.sumOpenByPayerAndCurrency(payer.getMemberId(), currency);
        BigDecimal projected = used.add(amount);
        if (projected.compareTo(limit) > 0) {
            throw new DomainException(
                    "CREDIT_LIMIT_EXCEEDED",
                    String.format(
                            "会员[%s] %s 额度上限 %s,当前已用 %s,本笔 %s,提交后合计 %s 将超限,已拦截新建义务",
                            payer.getName(), currency,
                            limit.toPlainString(), used.toPlainString(),
                            amount.toPlainString(), projected.toPlainString()));
        }
    }
}
