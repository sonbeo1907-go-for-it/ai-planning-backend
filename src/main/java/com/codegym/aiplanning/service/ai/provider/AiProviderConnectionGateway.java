package com.codegym.aiplanning.service.ai.provider;

import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiProviderConnectionGateway {

    private final Map<AiProviderProtocol, AiProviderConnectionTester> testers;

    public AiProviderConnectionGateway(List<AiProviderConnectionTester> testers) {
        EnumMap<AiProviderProtocol, AiProviderConnectionTester> byProtocol =
                new EnumMap<>(AiProviderProtocol.class);
        for (AiProviderConnectionTester tester : testers) {
            byProtocol.put(tester.protocol(), tester);
        }
        this.testers = Map.copyOf(byProtocol);
    }

    public ProviderConnectionResult test(
            AiProvider provider, AiProviderConfig config, String apiKey) {
        AiProviderConnectionTester tester = testers.get(provider.getProtocol());
        if (tester == null) {
            return ProviderConnectionResult.failure(
                    0L,
                    "UNSUPPORTED_PROTOCOL",
                    "No connection tester exists for this provider protocol.");
        }
        return tester.test(provider, config, apiKey);
    }
}
