package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.billing.AiCreditRate;
import com.codegym.aiplanning.entity.billing.AiCreditRateStatus;
import com.codegym.aiplanning.repository.billing.AiCreditRateRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class AiCreditPriceResolver {
    private final AiCreditRateRepository rates;

    public AiCreditPriceResolver(AiCreditRateRepository rates) {
        this.rates = rates;
    }

    public AiCreditRate resolve(AiPurpose purpose, String modelCategory) {
        String category = BillingValidation.category(modelCategory);
        Instant now = Instant.now();
        if (category != null) {
            var exact = rates.findFirstByPurposeAndModelCategoryAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                    purpose, category, AiCreditRateStatus.ACTIVE, now);
            if (exact.isPresent()) {
                return exact.get();
            }
        }
        return rates.findFirstByPurposeAndModelCategoryAndStatusAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
                purpose, null, AiCreditRateStatus.ACTIVE, now).orElseThrow(() ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                                "Chưa có giá AI Credit khả dụng cho mục đích này."));
    }
}
