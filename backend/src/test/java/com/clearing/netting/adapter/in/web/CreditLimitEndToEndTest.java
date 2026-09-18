package com.clearing.netting.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验收链路：设额度 → 调低额度 → 新建义务超限被拦截（HTTP 400 CREDIT_LIMIT_EXCEEDED）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreditLimitEndToEndTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("token").asText();
    }

    private String createMember(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("memberId").asText();
    }

    private String obligation(String payer, String payee, String amount) throws Exception {
        return JSON.createObjectNode()
                .put("payerMemberId", payer)
                .put("payeeMemberId", payee)
                .put("currency", "USD")
                .put("amount", new BigDecimal(amount))
                .put("tradeDate", "2026-09-18")
                .put("settleDate", "2026-09-18")
                .toString();
    }

    @Test
    void lowerLimitThenNewObligationIsBlocked() throws Exception {
        String operator = login("operator", "op123456");
        String viewer = login("viewer", "view123456");

        String payer = createMember(operator, "E2E Alpha");
        String payee = createMember(operator, "E2E Beta");

        // 初始额度 100 万，两笔合计 16 万可通过
        mockMvc.perform(put("/api/credit-limits")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + payer + "\",\"currency\":\"USD\",\"limitAmount\":1000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitAmount").value(1000000.00000000));

        mockMvc.perform(post("/api/obligations")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(obligation(payer, payee, "100000")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/obligations")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(obligation(payer, payee, "60000")))
                .andExpect(status().isOk());

        // 额度调低到 15 万（已用 16 万）
        mockMvc.perform(put("/api/credit-limits")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + payer + "\",\"currency\":\"USD\",\"limitAmount\":150000}"))
                .andExpect(status().isOk());

        // usage 接口反映超限
        String usage = mockMvc.perform(get("/api/credit-limits/usage")
                        .header("Authorization", "Bearer " + viewer)
                        .param("memberId", payer)
                        .param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitAmount").value(150000.00000000))
                .andExpect(jsonPath("$.usedAmount").value(160000.00000000))
                .andReturn().getResponse().getContentAsString();
        JsonNode usageNode = JSON.readTree(usage);
        org.junit.jupiter.api.Assertions.assertTrue(
                usageNode.get("availableAmount").decimalValue().signum() < 0);

        // 再新建一笔 1 元的义务 → 400 + CREDIT_LIMIT_EXCEEDED
        mockMvc.perform(post("/api/obligations")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(obligation(payer, payee, "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT_EXCEEDED"));

        // viewer 只读：可看列表，不可改额度
        mockMvc.perform(get("/api/credit-limits").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/credit-limits")
                        .header("Authorization", "Bearer " + viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + payer + "\",\"currency\":\"USD\",\"limitAmount\":1}"))
                .andExpect(status().isForbidden());

        // 调高回 20 万后，新义务恢复可建
        mockMvc.perform(put("/api/credit-limits")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + payer + "\",\"currency\":\"USD\",\"limitAmount\":200000}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/obligations")
                        .header("Authorization", "Bearer " + operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(obligation(payer, payee, "40000")))
                .andExpect(status().isOk());
    }
}
