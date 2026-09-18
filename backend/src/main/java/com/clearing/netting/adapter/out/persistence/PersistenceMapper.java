package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.entity.MemberCreditLimitJpaEntity;
import com.clearing.netting.adapter.out.persistence.entity.MemberCreditLimitId;
import com.clearing.netting.adapter.out.persistence.entity.MemberJpaEntity;
import com.clearing.netting.adapter.out.persistence.entity.NetPositionJpaEntity;
import com.clearing.netting.adapter.out.persistence.entity.NettingRunJpaEntity;
import com.clearing.netting.adapter.out.persistence.entity.ObligationJpaEntity;
import com.clearing.netting.adapter.out.persistence.entity.UserJpaEntity;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.model.UserAccount;

import java.math.BigDecimal;

final class PersistenceMapper {

    private PersistenceMapper() {
    }

    static Member toDomain(MemberJpaEntity e) {
        return new Member(e.getMemberId(), e.getName(), e.getStatus());
    }

    static Member toDomain(MemberJpaEntity e, java.util.List<MemberCreditLimitJpaEntity> limits) {
        java.util.Map<String, BigDecimal> limitMap = new java.util.HashMap<>();
        for (MemberCreditLimitJpaEntity l : limits) {
            limitMap.put(l.getId().getCurrency(), l.getLimitAmount());
        }
        return new Member(e.getMemberId(), e.getName(), e.getStatus(), limitMap);
    }

    static MemberJpaEntity toEntity(Member m) {
        MemberJpaEntity e = new MemberJpaEntity();
        e.setMemberId(m.getMemberId());
        e.setName(m.getName());
        e.setStatus(m.getStatus());
        return e;
    }

    static MemberCreditLimitJpaEntity toLimitEntity(String memberId, String currency, BigDecimal limit) {
        return new MemberCreditLimitJpaEntity(new MemberCreditLimitId(memberId, currency), limit);
    }

    static TradeObligation toDomain(ObligationJpaEntity e) {
        return new TradeObligation(
                e.getObligationId(),
                e.getPayerMemberId(),
                e.getPayeeMemberId(),
                e.getCurrency(),
                e.getAmount(),
                e.getTradeDate(),
                e.getSettleDate(),
                e.getStatus(),
                e.getNettingRunId());
    }

    static ObligationJpaEntity toEntity(TradeObligation o) {
        ObligationJpaEntity e = new ObligationJpaEntity();
        e.setObligationId(o.getObligationId());
        e.setPayerMemberId(o.getPayerMemberId());
        e.setPayeeMemberId(o.getPayeeMemberId());
        e.setCurrency(o.getCurrency());
        e.setAmount(o.getAmount());
        e.setTradeDate(o.getTradeDate());
        e.setSettleDate(o.getSettleDate());
        e.setStatus(o.getStatus());
        e.setNettingRunId(o.getNettingRunId());
        return e;
    }

    static NettingRun toDomain(NettingRunJpaEntity e) {
        return new NettingRun(
                e.getRunId(),
                e.getSettleDate(),
                e.getCurrency(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getFailureReason());
    }

    static NettingRunJpaEntity toEntity(NettingRun r) {
        NettingRunJpaEntity e = new NettingRunJpaEntity();
        e.setRunId(r.getRunId());
        e.setSettleDate(r.getSettleDate());
        e.setCurrency(r.getCurrency());
        e.setStatus(r.getStatus());
        e.setCreatedAt(r.getCreatedAt());
        e.setFailureReason(r.getFailureReason());
        return e;
    }

    static NetPosition toDomain(NetPositionJpaEntity e) {
        return new NetPosition(
                e.getPositionId(),
                e.getRunId(),
                e.getMemberId(),
                e.getCurrency(),
                e.getNetAmount());
    }

    static NetPositionJpaEntity toEntity(NetPosition p) {
        NetPositionJpaEntity e = new NetPositionJpaEntity();
        e.setPositionId(p.getPositionId());
        e.setRunId(p.getRunId());
        e.setMemberId(p.getMemberId());
        e.setCurrency(p.getCurrency());
        e.setNetAmount(p.getNetAmount());
        return e;
    }

    static UserAccount toDomain(UserJpaEntity e) {
        return new UserAccount(e.getUserId(), e.getUsername(), e.getPasswordHash(), e.getRole());
    }

    static UserJpaEntity toEntity(UserAccount u) {
        UserJpaEntity e = new UserJpaEntity();
        e.setUserId(u.getUserId());
        e.setUsername(u.getUsername());
        e.setPasswordHash(u.getPasswordHash());
        e.setRole(u.getRole());
        return e;
    }
}
