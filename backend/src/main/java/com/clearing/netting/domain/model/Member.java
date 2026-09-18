package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Member {
    private final String memberId;
    private String name;
    private MemberStatus status;
    private final Map<String, BigDecimal> creditLimits = new HashMap<>();

    public Member(String memberId, String name, MemberStatus status) {
        this(memberId, name, status, Map.of());
    }

    public Member(String memberId, String name, MemberStatus status, Map<String, BigDecimal> creditLimits) {
        this.memberId = Objects.requireNonNull(memberId);
        this.name = Objects.requireNonNull(name);
        this.status = Objects.requireNonNull(status);
        if (creditLimits != null) {
            creditLimits.forEach((ccy, limit) ->
                    this.creditLimits.put(normalizeCurrency(ccy), requirePositive(limit)));
        }
    }

    public static Member create(String name) {
        return new Member(UUID.randomUUID().toString(), name, MemberStatus.ACTIVE);
    }

    public void suspend() {
        this.status = MemberStatus.SUSPENDED;
    }

    public void activate() {
        this.status = MemberStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    /**
     * 设置某币种的额度上限(正数);覆盖原值。
     */
    public void setCreditLimit(String currency, BigDecimal limit) {
        creditLimits.put(normalizeCurrency(currency), requirePositive(limit));
    }

    public void removeCreditLimit(String currency) {
        creditLimits.remove(normalizeCurrency(currency));
    }

    /**
     * @return 某币种额度上限;未设置时返回 null,表示不限制。
     */
    public BigDecimal getCreditLimit(String currency) {
        return creditLimits.get(normalizeCurrency(currency));
    }

    public Map<String, BigDecimal> getCreditLimits() {
        return Collections.unmodifiableMap(creditLimits);
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        return currency.trim().toUpperCase();
    }

    private static BigDecimal requirePositive(BigDecimal limit) {
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("credit limit must be positive");
        }
        return limit;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public MemberStatus getStatus() {
        return status;
    }
}
