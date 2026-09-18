package com.clearing.netting.application;

import java.math.BigDecimal;

/**
 * 会员额度视图:额度上限及该会员作为付款方在某币种下的 OPEN 义务已用金额。
 */
public record CreditLimitView(
        String memberId,
        String memberName,
        String currency,
        BigDecimal limitAmount,
        BigDecimal usedAmount) {
}
