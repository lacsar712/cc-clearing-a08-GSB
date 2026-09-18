package com.clearing.netting.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MemberCreditLimitId implements Serializable {

    @Column(name = "member_id", length = 64)
    private String memberId;

    @Column(name = "currency", length = 8)
    private String currency;

    protected MemberCreditLimitId() {
    }

    public MemberCreditLimitId(String memberId, String currency) {
        this.memberId = memberId;
        this.currency = currency;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MemberCreditLimitId that)) {
            return false;
        }
        return Objects.equals(memberId, that.memberId) && Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, currency);
    }
}
