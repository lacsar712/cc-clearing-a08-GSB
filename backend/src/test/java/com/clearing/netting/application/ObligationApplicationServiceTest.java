package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
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

class ObligationApplicationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    private FakeMemberRepository members;
    private FakeObligationRepository obligations;
    private ObligationApplicationService service;

    @BeforeEach
    void setUp() {
        members = new FakeMemberRepository();
        obligations = new FakeObligationRepository();
        service = new ObligationApplicationService(obligations, members);

        Member payer = new Member("P", "Payer Bank", MemberStatus.ACTIVE);
        Member payee = new Member("R", "Payee Bank", MemberStatus.ACTIVE);
        members.save(payer);
        members.save(payee);
    }

    @Test
    void allowsWhenNoLimitConfigured() {
        service.create("P", "R", "USD", new BigDecimal("5000000"), TODAY, TODAY);
        assertEquals(1, obligations.saved.size());
    }

    @Test
    void allowsWhenWithinLimit() {
        members.setLimit("P", "USD", new BigDecimal("100000"));
        obligations.openSum = new BigDecimal("60000");

        service.create("P", "R", "USD", new BigDecimal("40000"), TODAY, TODAY);

        assertEquals(1, obligations.saved.size());
    }

    @Test
    void blocksWhenProjectedExceedsLimit() {
        members.setLimit("P", "USD", new BigDecimal("100000"));
        obligations.openSum = new BigDecimal("90000");

        DomainException ex = assertThrows(DomainException.class, () ->
                service.create("P", "R", "USD", new BigDecimal("10001"), TODAY, TODAY));
        assertEquals("CREDIT_LIMIT_EXCEEDED", ex.getCode());
        assertTrue(ex.getMessage().contains("USD"));
        assertEquals(0, obligations.saved.size());
    }

    @Test
    void boundaryEqualsLimitIsAllowed() {
        members.setLimit("P", "USD", new BigDecimal("100000"));
        obligations.openSum = new BigDecimal("50000");

        service.create("P", "R", "USD", new BigDecimal("50000"), TODAY, TODAY);

        assertEquals(1, obligations.saved.size());
    }

    @Test
    void limitIsPerCurrency() {
        members.setLimit("P", "USD", new BigDecimal("100"));
        obligations.openSum = BigDecimal.ZERO;

        service.create("P", "R", "EUR", new BigDecimal("999999"), TODAY, TODAY);
        assertEquals(1, obligations.saved.size());
    }

    private static class FakeMemberRepository implements MemberRepositoryPort {
        private final Map<String, Member> store = new HashMap<>();

        void setLimit(String memberId, String currency, BigDecimal limit) {
            Member m = store.get(memberId);
            m.setCreditLimit(currency, limit);
        }

        @Override
        public Member save(Member member) {
            store.put(member.getMemberId(), member);
            return member;
        }

        @Override
        public Optional<Member> findById(String memberId) {
            return Optional.ofNullable(store.get(memberId));
        }

        @Override
        public List<Member> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<Member> findByIds(Iterable<String> memberIds) {
            List<Member> result = new ArrayList<>();
            memberIds.forEach(id -> Optional.ofNullable(store.get(id)).ifPresent(result::add));
            return result;
        }
    }

    private static class FakeObligationRepository implements ObligationRepositoryPort {
        private final List<TradeObligation> saved = new ArrayList<>();
        private BigDecimal openSum = BigDecimal.ZERO;

        @Override
        public TradeObligation save(TradeObligation obligation) {
            saved.add(obligation);
            return obligation;
        }

        @Override
        public List<TradeObligation> saveAll(List<TradeObligation> obligations) {
            saved.addAll(obligations);
            return obligations;
        }

        @Override
        public Optional<TradeObligation> findById(String obligationId) {
            return saved.stream().filter(o -> o.getObligationId().equals(obligationId)).findFirst();
        }

        @Override
        public List<TradeObligation> findAll() {
            return saved;
        }

        @Override
        public List<TradeObligation> findByFilters(String currency, LocalDate settleDate, ObligationStatus status) {
            return saved;
        }

        @Override
        public List<TradeObligation> findOpenBySettleDateAndCurrency(LocalDate settleDate, String currency) {
            return saved;
        }

        @Override
        public List<TradeObligation> findByNettingRunId(String runId) {
            return saved;
        }

        @Override
        public BigDecimal sumOpenByPayerAndCurrency(String payerMemberId, String currency) {
            return openSum;
        }

        @Override
        public Map<String, Map<String, BigDecimal>> sumOpenAmountsGroupedByPayerAndCurrency() {
            return Map.of();
        }
    }
}
