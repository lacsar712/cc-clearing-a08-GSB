package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.CreditLimitView;
import com.clearing.netting.application.MemberApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/members/credit-limits")
public class CreditLimitController {

    private final MemberApplicationService memberService;

    public CreditLimitController(MemberApplicationService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<CreditLimitResponse> list() {
        AuthContext.require();
        return memberService.listCreditLimits().stream()
                .map(CreditLimitResponse::from)
                .collect(Collectors.toList());
    }

    @PutMapping
    public CreditLimitResponse upsert(@Valid @RequestBody UpsertCreditLimitRequest request) {
        AuthContext.requireOperator();
        return CreditLimitResponse.from(memberService.upsertCreditLimit(
                request.memberId(), request.currency(), request.limitAmount()));
    }

    @DeleteMapping
    public void remove(@RequestParam String memberId, @RequestParam String currency) {
        AuthContext.requireOperator();
        memberService.removeCreditLimit(memberId, currency);
    }

    public record UpsertCreditLimitRequest(
            @NotBlank String memberId,
            @NotBlank String currency,
            @NotNull @DecimalMin(value = "0.00000001", message = "must be positive") BigDecimal limitAmount) {
    }

    public record CreditLimitResponse(
            String memberId,
            String memberName,
            String currency,
            BigDecimal limitAmount,
            BigDecimal usedAmount) {
        static CreditLimitResponse from(CreditLimitView v) {
            return new CreditLimitResponse(
                    v.memberId(), v.memberName(), v.currency(), v.limitAmount(), v.usedAmount());
        }
    }
}
