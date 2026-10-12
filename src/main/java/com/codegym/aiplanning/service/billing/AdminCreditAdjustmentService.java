package com.codegym.aiplanning.service.billing;

import static com.codegym.aiplanning.service.billing.BillingValidation.*;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.BillingProperties;
import com.codegym.aiplanning.controller.admin.billing.dto.AdminBillingDtos.*;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.billing.*;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.billing.*;
import com.codegym.aiplanning.service.audit.AuditLogService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCreditAdjustmentService {
    private final UserAccountRepository users;
    private final CreditWalletRepository wallets;
    private final CreditLedgerEntryRepository ledger;
    private final CreditWalletAtomicInitializer initializer;
    private final AuditLogService audit;
    private final BillingProperties properties;

    public AdminCreditAdjustmentService(UserAccountRepository users, CreditWalletRepository wallets,
            CreditLedgerEntryRepository ledger, CreditWalletAtomicInitializer initializer,
            AuditLogService audit, BillingProperties properties) {
        this.users = users;
        this.wallets = wallets;
        this.ledger = ledger;
        this.initializer = initializer;
        this.audit = audit;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public WalletView lookup(String account) {
        if (account == null || account.isBlank() || account.length() > 254) {
            throw invalid("Nhập email hoặc mã tài khoản.");
        }
        UserAccount user;
        try {
            user = users.findById(UUID.fromString(account.trim())).orElseThrow(this::notFound);
        } catch (IllegalArgumentException exception) {
            user = users.findByEmailIgnoreCase(account.trim()).orElseThrow(this::notFound);
        }
        return preview(requireUser(user));
    }

    @Transactional(readOnly = true)
    public WalletView get(UUID userId) {
        return preview(requireUser(users.findById(userId).orElseThrow(this::notFound)));
    }

    @Transactional
    public AdjustmentView adjust(UUID actor, String email, UUID userId, String requestKey, AdjustmentInput input) {
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9_-]{1,60}")) {
            throw invalid("Idempotency-Key không hợp lệ.");
        }
        String reason = input.reason().trim();
        if (input.delta() == 0 || reason.isBlank() || reason.matches("(?s).*\\p{Cntrl}.*")) {
            throw invalid("Điều chỉnh cần số Credit khác 0 và lý do hợp lệ.");
        }
        var user = requireUser(users.findByIdForUpdate(userId).orElseThrow(this::notFound));
        String key = "ADJUSTMENT:" + actor + ":" + userId + ":" + requestKey;
        var existing = ledger.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            var entry = existing.get();
            if (entry.getAvailableDelta() != input.delta() || !reason.equals(entry.getDescription())
                    || !entry.getUser().getId().equals(userId) || entry.getEntryType() != LedgerEntryType.ADJUSTMENT) {
                throw new BusinessException(ErrorCode.CONFLICT, "Khóa yêu cầu đã dùng cho một điều chỉnh khác.");
            }
            return AdjustmentView.from(entry, true);
        }
        var current = wallets.findByUserIdForUpdate(userId);
        if (!Objects.equals(current.map(CreditWallet::getVersion).orElse(null), input.version())) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "Số dư đã thay đổi. Hãy kiểm tra lại trước khi điều chỉnh.");
        }
        var wallet = initializer.getOrCreate(userId);
        long available = safeSum(wallet.getAvailableCredits(), input.delta());
        wallet.updateProjection(available, wallet.getReservedCredits());
        wallets.saveAndFlush(wallet);
        var entry = ledger.saveAndFlush(CreditLedgerEntry.create(wallet, user, LedgerEntryType.ADJUSTMENT,
                input.delta(), 0, available, wallet.getReservedCredits(), LedgerReferenceType.ADJUSTMENT,
                UUID.randomUUID(), key, reason));
        audit.logAction(actor, email, AuditEventAction.CREDIT_WALLET_ADJUSTED,
                "CreditLedgerEntry", entry.getId().toString());
        return AdjustmentView.from(entry, false);
    }

    private WalletView preview(UserAccount user) {
        return wallets.findByUserId(user.getId()).map(wallet -> new WalletView(user.getId(), user.getEmail(),
                wallet.getId(), wallet.getVersion(), wallet.getAvailableCredits(), wallet.getReservedCredits(), true))
                .orElseGet(() -> new WalletView(user.getId(), user.getEmail(), null, null,
                        properties.getWelcomeCredits(), 0, false));
    }

    private UserAccount requireUser(UserAccount user) {
        if (user.getRole() != UserRole.USER) {
            throw notFound();
        }
        return user;
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài khoản USER.");
    }
}
