package com.codegym.aiplanning.service.ai.provider;

public final class AiUsageHolder {

    private static final ThreadLocal<AiUsage> CURRENT_USAGE = new ThreadLocal<>();

    private AiUsageHolder() {}

    public static void set(AiUsage usage) {
        CURRENT_USAGE.set(usage);
    }

    public static AiUsage get() {
        return CURRENT_USAGE.get();
    }

    public static AiUsage getAndClear() {
        AiUsage usage = CURRENT_USAGE.get();
        CURRENT_USAGE.remove();
        return usage;
    }

    public static void clear() {
        CURRENT_USAGE.remove();
    }
}
