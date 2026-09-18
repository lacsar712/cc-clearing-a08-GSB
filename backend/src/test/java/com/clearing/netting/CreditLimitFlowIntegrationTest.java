package com.clearing.netting;

import com.clearing.netting.application.CreditLimitView;
import com.clearing.netting.application.MemberApplicationService;
import com.clearing.netting.application.ObligationApplicationService;
import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CreditLimitFlowIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    @Autowired
    private MemberApplicationService members;

    @Autowired
    private ObligationApplicationService obligations;

    @Test
    void loweredLimitBlocksNewObligationAndPersistsAcrossReload() {
        Member payer = members.createMember("Limit Payer");
        Member payee = members.createMember("Limit Payee");
        String payerId = payer.getMemberId();

        // 未设额度:可自由新建
        obligations.create(payerId, payee.getMemberId(), "USD", new BigDecimal("100000"), TODAY, TODAY);

        // 设置较高额度并打到边界(150000 == 上限)
        members.upsertCreditLimit(payerId, "USD", new BigDecimal("150000"));
        obligations.create(payerId, payee.getMemberId(), "USD", new BigDecimal("50000"), TODAY, TODAY);

        // 额度调低到 120000(已用 150000 已超):再新建哪怕 1 也必须被拦截
        members.upsertCreditLimit(payerId, "USD", new BigDecimal("120000"));

        DomainException ex = assertThrows(DomainException.class, () ->
                obligations.create(payerId, payee.getMemberId(), "USD", new BigDecimal("1"), TODAY, TODAY));
        assertEquals("CREDIT_LIMIT_EXCEEDED", ex.getCode());
        assertTrue(ex.getMessage().contains("150001"));
        assertTrue(ex.getMessage().contains("120000"));

        // 额度视图反映上限与已用
        CreditLimitView view = members.listCreditLimits().stream()
                .filter(v -> v.memberId().equals(payerId) && v.currency().equals("USD"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, view.limitAmount().compareTo(new BigDecimal("120000.00000000")));
        assertEquals(0, view.usedAmount().compareTo(new BigDecimal("150000.00000000")));

        // 跨持久化重新加载,额度仍在
        Member reloaded = members.getMember(payerId);
        assertNotNull(reloaded.getCreditLimit("USD"));
        assertEquals(0, reloaded.getCreditLimit("USD").compareTo(new BigDecimal("120000.00000000")));

        // 调高额度后恢复可建
        members.upsertCreditLimit(payerId, "USD", new BigDecimal("200000"));
        obligations.create(payerId, payee.getMemberId(), "USD", new BigDecimal("50000"), TODAY, TODAY);

        // 删除额度后不再受限
        members.removeCreditLimit(payerId, "USD");
        obligations.create(payerId, payee.getMemberId(), "USD", new BigDecimal("9999999"), TODAY, TODAY);
    }
}
