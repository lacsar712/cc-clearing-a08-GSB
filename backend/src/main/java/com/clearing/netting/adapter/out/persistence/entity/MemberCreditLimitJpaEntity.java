package com.clearing.netting.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "member_credit_limits")
public class MemberCreditLimitJpaEntity {

    @EmbeddedId
    private MemberCreditLimitId id;

    @Column(name = "limit_amount", nullable = false, precision = 28, scale = 8)
    private BigDecimal limitAmount;

    protected MemberCreditLimitJpaEntity() {
    }

    public MemberCreditLimitJpaEntity(MemberCreditLimitId id, BigDecimal limitAmount) {
        this.id = id;
        this.limitAmount = limitAmount;
    }

    public MemberCreditLimitId getId() {
        return id;
    }

    public void setId(MemberCreditLimitId id) {
        this.id = id;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }
}
