package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ObligationRepositoryPort {
    TradeObligation save(TradeObligation obligation);

    List<TradeObligation> saveAll(List<TradeObligation> obligations);

    Optional<TradeObligation> findById(String obligationId);

    List<TradeObligation> findAll();

    List<TradeObligation> findByFilters(String currency, LocalDate settleDate, ObligationStatus status);

    List<TradeObligation> findOpenBySettleDateAndCurrency(LocalDate settleDate, String currency);

    List<TradeObligation> findByNettingRunId(String runId);

    /** 某会员作为付款方、指定币种的全部 OPEN 义务金额合计。 */
    BigDecimal sumOpenAmountByPayerAndCurrency(String payerMemberId, String currency);
}
