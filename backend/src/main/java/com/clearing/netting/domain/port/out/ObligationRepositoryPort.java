package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ObligationRepositoryPort {
    TradeObligation save(TradeObligation obligation);

    List<TradeObligation> saveAll(List<TradeObligation> obligations);

    Optional<TradeObligation> findById(String obligationId);

    List<TradeObligation> findAll();

    List<TradeObligation> findByFilters(String currency, LocalDate settleDate, ObligationStatus status);

    List<TradeObligation> findOpenBySettleDateAndCurrency(LocalDate settleDate, String currency);

    List<TradeObligation> findByNettingRunId(String runId);

    /**
     * 付款方在某币种下尚未轧差(OPEN)义务的金额合计;无记录返回 0。
     */
    BigDecimal sumOpenByPayerAndCurrency(String payerMemberId, String currency);

    /**
     * @return 外层 key=付款方会员 ID,内层 key=币种,value=OPEN 义务金额合计。
     */
    Map<String, Map<String, BigDecimal>> sumOpenAmountsGroupedByPayerAndCurrency();
}
