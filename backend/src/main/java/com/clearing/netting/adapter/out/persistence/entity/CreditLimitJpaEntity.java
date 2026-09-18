package com.clearing.netting.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(name = "member_credit_limits", uniqueConstraints =
        @UniqueConstraint(name = "uk_credit_limit_member_currency", columnNames = {"memberId", "currency"}))
public class CreditLimitJpaEntity {

    @Id
    @Column(length = 64)
    private String limitId;

    @Column(nullable = false, length = 64)
    private String memberId;

    @Column(nullable = false, length = 16)
    private String currency;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal limitAmount;

    public String getLimitId() {
        return limitId;
    }

    public void setLimitId(String limitId) {
        this.limitId = limitId;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }
}
