package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.ObligationJpaEntity;
import com.clearing.netting.domain.model.ObligationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ObligationJpaRepository
        extends JpaRepository<ObligationJpaEntity, String>, JpaSpecificationExecutor<ObligationJpaEntity> {

    List<ObligationJpaEntity> findBySettleDateAndCurrencyIgnoreCaseAndStatus(
            LocalDate settleDate, String currency, ObligationStatus status);

    List<ObligationJpaEntity> findByNettingRunId(String nettingRunId);

    @Query("select coalesce(sum(o.amount), 0) from ObligationJpaEntity o "
            + "where o.payerMemberId = :payerMemberId and o.currency = :currency and o.status = :status")
    BigDecimal sumAmountByPayerAndCurrencyAndStatus(
            @Param("payerMemberId") String payerMemberId,
            @Param("currency") String currency,
            @Param("status") ObligationStatus status);

    @Query("select o.payerMemberId, o.currency, coalesce(sum(o.amount), 0) from ObligationJpaEntity o "
            + "where o.status = :status group by o.payerMemberId, o.currency")
    List<Object[]> sumAmountsGroupedByPayerAndCurrencyAndStatus(@Param("status") ObligationStatus status);
}
