package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.service.ai.AiProviderSelector;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCreditQuoteService {
    public record Quote(UUID rateId, UUID providerConfigId, AiPurpose purpose,
            String modelCategory, long creditCost) {}

    private final AiProviderSelector providers;
    private final AiCreditPriceResolver prices;

    public AiCreditQuoteService(AiProviderSelector providers, AiCreditPriceResolver prices) {
        this.providers = providers;
        this.prices = prices;
    }

    @Transactional(readOnly = true)
    public Quote quote(AiPurpose purpose) {
        var provider = providers.requireDefault(purpose);
        var rate = prices.resolve(purpose, provider.getPricingCategory());
        return new Quote(rate.getId(), provider.getId(), purpose, rate.getModelCategory(), rate.getCreditCost());
    }
}
