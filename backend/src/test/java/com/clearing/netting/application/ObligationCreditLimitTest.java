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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObligationCreditLimitTest {

    private static final String PAYER = "m-1";
    private static final String PAYEE = "m-2";
    private static final String CCY = "USD";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    private Map<String, Member> members;
    private Map<String, CreditLimit> limits;
    private List<TradeObligation> obligations;
    private ObligationApplicationService obligationService;
    private CreditLimitApplicationService creditLimitService;

    @BeforeEach
    void setUp() {
        members = new HashMap<>();
        members.put(PAYER, new Member(PAYER, "Alpha Bank", MemberStatus.ACTIVE));
        members.put(PAYEE, new Member(PAYEE, "Beta Securities", MemberStatus.ACTIVE));
        limits = new HashMap<>();
        obligations = new ArrayList<>();

        MemberRepositoryPort memberRepo = new MemberRepositoryPort() {
            @Override
            public Member save(Member member) {
                members.put(member.getMemberId(), member);
                return member;
            }

            @Override
            public Optional<Member> findById(String memberId) {
                return Optional.ofNullable(members.get(memberId));
            }

            @Override
            public List<Member> findAll() {
                return new ArrayList<>(members.values());
            }

            @Override
            public List<Member> findByIds(Iterable<String> memberIds) {
                List<Member> result = new ArrayList<>();
                memberIds.forEach(id -> {
                    if (members.containsKey(id)) result.add(members.get(id));
                });
                return result;
            }
        };

        CreditLimitRepositoryPort limitRepo = new CreditLimitRepositoryPort() {
            @Override
            public CreditLimit save(CreditLimit creditLimit) {
                limits.put(key(creditLimit.getMemberId(), creditLimit.getCurrency()), creditLimit);
                return creditLimit;
            }

            @Override
            public Optional<CreditLimit> findByMemberAndCurrency(String memberId, String currency) {
                return Optional.ofNullable(limits.get(key(memberId, currency.toUpperCase())));
            }

            @Override
            public List<CreditLimit> findAll() {
                return new ArrayList<>(limits.values());
            }

            @Override
            public List<CreditLimit> findByMemberIds(Iterable<String> memberIds) {
                return new ArrayList<>(limits.values());
            }

            @Override
            public void deleteByMemberAndCurrency(String memberId, String currency) {
                limits.remove(key(memberId, currency.toUpperCase()));
            }
        };

        ObligationRepositoryPort obligationRepo = new ObligationRepositoryPort() {
            @Override
            public TradeObligation save(TradeObligation obligation) {
                obligations.add(obligation);
                return obligation;
            }

            @Override
            public List<TradeObligation> saveAll(List<TradeObligation> list) {
                obligations.addAll(list);
                return list;
            }

            @Override
            public Optional<TradeObligation> findById(String obligationId) {
                return obligations.stream().filter(o -> o.getObligationId().equals(obligationId)).findFirst();
            }

            @Override
            public List<TradeObligation> findAll() {
                return obligations;
            }

            @Override
            public List<TradeObligation> findByFilters(String currency, LocalDate settleDate, ObligationStatus status) {
                return obligations;
            }

            @Override
            public List<TradeObligation> findOpenBySettleDateAndCurrency(LocalDate settleDate, String currency) {
                return obligations;
            }

            @Override
            public List<TradeObligation> findByNettingRunId(String runId) {
                return obligations;
            }

            @Override
            public BigDecimal sumOpenAmountByPayerAndCurrency(String payerMemberId, String currency) {
                return obligations.stream()
                        .filter(o -> o.getPayerMemberId().equals(payerMemberId))
                        .filter(o -> o.getCurrency().equals(currency.toUpperCase()))
                        .filter(o -> o.getStatus() == ObligationStatus.OPEN)
                        .map(TradeObligation::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        };

        obligationService = new ObligationApplicationService(obligationRepo, memberRepo, limitRepo);
        creditLimitService = new CreditLimitApplicationService(limitRepo, memberRepo, obligationRepo);
    }

    private static String key(String memberId, String currency) {
        return memberId + "#" + currency;
    }

    private void createObligation(String amount) {
        obligationService.create(PAYER, PAYEE, CCY, new BigDecimal(amount), TODAY, TODAY);
    }

    @Test
    void noLimitConfigured_allowsCreation() {
        createObligation("999999999");
        assertEquals(1, obligations.size());
    }

    @Test
    void withinLimit_allowsCreation() {
        creditLimitService.setLimit(PAYER, CCY, new BigDecimal("100000"));
        createObligation("60000");
        createObligation("40000");
        assertEquals(2, obligations.size());
    }

    @Test
    void exceedingLimit_isRejected() {
        creditLimitService.setLimit(PAYER, CCY, new BigDecimal("100000"));
        createObligation("60000");
        DomainException ex = assertThrows(DomainException.class, () -> createObligation("40000.00000001"));
        assertEquals("CREDIT_LIMIT_EXCEEDED", ex.getCode());
        assertEquals(1, obligations.size(), "超限义务不得落库");
    }

    @Test
    void acceptance_lowerLimitThenNewObligationShowsExceeded() {
        // 初始较高额度，两笔合计 16 万，OPEN 占用 16 万
        creditLimitService.setLimit(PAYER, CCY, new BigDecimal("1000000"));
        createObligation("100000");
        createObligation("60000");
        // 额度调低到 15 万：占用 16 万已超限，任意正数新义务都必须拦截
        creditLimitService.setLimit(PAYER, CCY, new BigDecimal("150000"));
        CreditLimitApplicationService.LimitUsage usage = creditLimitService.getUsage(PAYER, CCY);
        assertEquals(0, new BigDecimal("150000").compareTo(usage.limitAmount()));
        assertEquals(0, new BigDecimal("160000").compareTo(usage.usedAmount()));
        assertTrue(usage.limitAmount().subtract(usage.usedAmount()).signum() < 0, "可用额度应为负");
        DomainException ex = assertThrows(DomainException.class, () -> createObligation("1"));
        assertEquals("CREDIT_LIMIT_EXCEEDED", ex.getCode());
        assertEquals(2, obligations.size());
    }

    @Test
    void nettedObligationsDoNotConsumeLimit() {
        creditLimitService.setLimit(PAYER, CCY, new BigDecimal("100"));
        createObligation("100");
        // 模拟轧差后 OPEN 占用归零（桩按 status 过滤），新义务可再用满额度
        obligations.forEach(o -> o.markNetted("run-1"));
        createObligation("100");
        assertEquals(2, obligations.size());
    }
}
