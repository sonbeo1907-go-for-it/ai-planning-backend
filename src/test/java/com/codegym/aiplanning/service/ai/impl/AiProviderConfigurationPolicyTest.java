package com.codegym.aiplanning.service.ai.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.AiProviderProperties;
import org.junit.jupiter.api.Test;

class AiProviderConfigurationPolicyTest {

    @Test
    void productionPolicyAcceptsDynamicHttpsProviderUrls() {
        AiProviderConfigurationPolicy policy =
                new AiProviderConfigurationPolicy(new AiProviderProperties(false));

        assertThat(policy.validateAndNormalizeBaseUrl("https://api.deepseek.com/v1/"))
                .isEqualTo("https://api.deepseek.com/v1");
        assertThat(policy.normalizeProviderCode("private_model_gateway"))
                .isEqualTo("PRIVATE_MODEL_GATEWAY");

        assertThatThrownBy(() -> policy.validateAndNormalizeBaseUrl("http://127.0.0.1:8080"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.AI_PROVIDER_INVALID_CONFIGURATION));
    }

    @Test
    void controlledEnvironmentMayOptIntoHttpUrl() {
        AiProviderConfigurationPolicy policy =
                new AiProviderConfigurationPolicy(new AiProviderProperties(true));

        assertThat(policy.validateAndNormalizeBaseUrl("http://127.0.0.1:8080/"))
                .isEqualTo("http://127.0.0.1:8080");
    }
}
