package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.CreditLimitApplicationService;
import com.clearing.netting.domain.model.CreditLimit;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
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
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/credit-limits")
public class CreditLimitController {

    private final CreditLimitApplicationService creditLimitService;
    private final MemberRepositoryPort memberRepository;

    public CreditLimitController(
            CreditLimitApplicationService creditLimitService,
            MemberRepositoryPort memberRepository) {
        this.creditLimitService = creditLimitService;
        this.memberRepository = memberRepository;
    }

    /** 登录用户均可查看额度列表。 */
    @GetMapping
    public List<CreditLimitResponse> list() {
        AuthContext.require();
        List<CreditLimit> limits = creditLimitService.listLimits();
        List<String> memberIds = limits.stream().map(CreditLimit::getMemberId).distinct().collect(Collectors.toList());
        Map<String, String> names = memberRepository.findByIds(memberIds).stream()
                .collect(Collectors.toMap(Member::getMemberId, Member::getName));
        return limits.stream()
                .map(l -> CreditLimitResponse.from(l, names.get(l.getMemberId())))
                .collect(Collectors.toList());
    }

    /** 新建义务页实时查询某会员/币种的额度占用。 */
    @GetMapping("/usage")
    public UsageResponse usage(
            @RequestParam String memberId,
            @RequestParam String currency) {
        AuthContext.require();
        CreditLimitApplicationService.LimitUsage usage = creditLimitService.getUsage(memberId, currency);
        return new UsageResponse(
                usage.memberId(),
                usage.currency(),
                usage.limitAmount(),
                usage.usedAmount(),
                usage.limitAmount() == null
                        ? null
                        : usage.limitAmount().subtract(usage.usedAmount()));
    }

    /** 设置/调整额度上限，仅操作员。 */
    @PutMapping
    public CreditLimitResponse setLimit(@Valid @RequestBody SetCreditLimitRequest request) {
        AuthContext.requireOperator();
        CreditLimit limit = creditLimitService.setLimit(
                request.memberId(), request.currency(), request.limitAmount());
        String memberName = memberRepository.findById(limit.getMemberId())
                .map(Member::getName).orElse(null);
        return CreditLimitResponse.from(limit, memberName);
    }

    /** 删除额度配置（恢复为不限额），仅操作员。 */
    @DeleteMapping
    public void remove(@RequestParam String memberId, @RequestParam String currency) {
        AuthContext.requireOperator();
        creditLimitService.removeLimit(memberId, currency);
    }

    public record SetCreditLimitRequest(
            @NotBlank String memberId,
            @NotBlank String currency,
            @NotNull @DecimalMin(value = "0.00000000", message = "must be zero or positive") BigDecimal limitAmount) {
    }

    public record CreditLimitResponse(
            String limitId, String memberId, String memberName, String currency, BigDecimal limitAmount) {
        static CreditLimitResponse from(CreditLimit l, String memberName) {
            return new CreditLimitResponse(
                    l.getLimitId(), l.getMemberId(), memberName, l.getCurrency(), l.getLimitAmount());
        }
    }

    public record UsageResponse(
            String memberId,
            String currency,
            BigDecimal limitAmount,
            BigDecimal usedAmount,
            BigDecimal availableAmount) {
    }
}
