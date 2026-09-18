package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 会员在单一币种上的额度上限。未配置额度时视为不限额。
 */
public class CreditLimit {
    private final String limitId;
    private final String memberId;
    private final String currency;
    private BigDecimal limitAmount;

    public CreditLimit(String limitId, String memberId, String currency, BigDecimal limitAmount) {
        this.limitId = Objects.requireNonNull(limitId);
        this.memberId = Objects.requireNonNull(memberId);
        this.currency = Objects.requireNonNull(currency).toUpperCase();
        this.limitAmount = normalize(limitAmount);
    }

    public static CreditLimit create(String memberId, String currency, BigDecimal limitAmount) {
        if (limitAmount == null || limitAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("limitAmount must be zero or positive");
        }
        return new CreditLimit(java.util.UUID.randomUUID().toString(), memberId, currency, limitAmount);
    }

    public void updateLimit(BigDecimal limitAmount) {
        if (limitAmount == null || limitAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("limitAmount must be zero or positive");
        }
        this.limitAmount = normalize(limitAmount);
    }

    /**
     * 新增一笔占用后是否超限。
     */
    public boolean isExceeded(BigDecimal usedAmount, BigDecimal newAmount) {
        BigDecimal total = (usedAmount == null ? BigDecimal.ZERO : usedAmount).add(newAmount);
        return total.compareTo(limitAmount) > 0;
    }

    private static BigDecimal normalize(BigDecimal v) {
        return Objects.requireNonNull(v).setScale(8, RoundingMode.HALF_UP);
    }

    public String getLimitId() {
        return limitId;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }
}
