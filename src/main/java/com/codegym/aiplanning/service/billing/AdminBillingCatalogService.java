package com.codegym.aiplanning.service.billing;

import static com.codegym.aiplanning.service.billing.BillingValidation.*;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import com.codegym.aiplanning.config.BillingProperties;
import com.codegym.aiplanning.controller.admin.billing.dto.AdminBillingDtos.*;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.audit.AuditEventAction;
import com.codegym.aiplanning.entity.billing.*;
import com.codegym.aiplanning.repository.billing.*;
import com.codegym.aiplanning.service.audit.AuditLogService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBillingCatalogService {
    private final CreditPackageRepository packages;
    private final AiCreditRateRepository rates;
    private final BillingProperties properties;
    private final AuditLogService audit;

    public AdminBillingCatalogService(CreditPackageRepository packages, AiCreditRateRepository rates,
            BillingProperties properties, AuditLogService audit) {
        this.packages = packages;
        this.rates = rates;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<PackageView> packages(CreditPackageStatus status, Pageable page) {
        return packages.findFiltered(status, bounded(page)).map(PackageView::from);
    }

    @Transactional
    public PackageView createPackage(UUID actor, String email, PackageInput input) {
        validatePackage(input);
        var value = packages.saveAndFlush(new CreditPackage(input.packageCode(), input.name().trim(),
                input.priceVnd(), input.baseCredits(), input.bonusCredits(), CreditPackageStatus.ARCHIVED,
                input.sortOrder()));
        audit.logAction(actor, email, AuditEventAction.CREDIT_PACKAGE_CREATED, "CreditPackage", value.getId().toString());
        return PackageView.from(value);
    }

    @Transactional
    public PackageView updatePackage(UUID actor, String email, UUID id, PackageInput input) {
        var value = requirePackage(id);
        requireVersion(value.getVersion(), input.version());
        validatePackage(input);
        if (!value.getPackageCode().equals(input.packageCode())) {
            throw invalid("Mã gói không thể thay đổi sau khi tạo.");
        }
        value.setName(input.name().trim());
        value.setPriceVnd(input.priceVnd());
        value.setBaseCredits(input.baseCredits());
        value.setBonusCredits(input.bonusCredits());
        value.setSortOrder(input.sortOrder());
        packages.saveAndFlush(value);
        audit.logAction(actor, email, AuditEventAction.CREDIT_PACKAGE_UPDATED, "CreditPackage", id.toString());
        return PackageView.from(value);
    }

    @Transactional
    public PackageView packageStatus(UUID actor, String email, UUID id, PackageStatusInput input) {
        var value = requirePackage(id);
        version(value.getVersion(), input.version());
        if (value.getStatus() != input.status()) {
            if (input.status() == CreditPackageStatus.ACTIVE) {
                validatePrice(value.getPriceVnd());
            }
            value.setStatus(input.status());
            packages.saveAndFlush(value);
            audit.logAction(actor, email, input.status() == CreditPackageStatus.ACTIVE
                    ? AuditEventAction.CREDIT_PACKAGE_ACTIVATED : AuditEventAction.CREDIT_PACKAGE_ARCHIVED,
                    "CreditPackage", id.toString());
        }
        return PackageView.from(value);
    }

    @Transactional(readOnly = true)
    public Page<RateView> rates(AiPurpose purpose, AiCreditRateStatus status, Pageable page) {
        return rates.findFiltered(purpose, status, bounded(page)).map(RateView::from);
    }

    @Transactional(readOnly = true)
    public RateActivationView activationPreview(UUID id) {
        var selected = rates.findById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy giá Credit."));
        var current = rates.findFirstByPurposeAndModelCategoryAndStatusOrderByEffectiveFromDesc(
                selected.getPurpose(), selected.getModelCategory(), AiCreditRateStatus.ACTIVE);
        return new RateActivationView(RateView.from(selected), current.map(RateView::from).orElse(null));
    }

    @Transactional
    public RateView createRate(UUID actor, String email, RateInput input) {
        var value = rates.saveAndFlush(AiCreditRate.create(input.purpose(), category(input.modelCategory()),
                input.creditCost(), AiCreditRateStatus.ARCHIVED, Instant.now()));
        audit.logAction(actor, email, AuditEventAction.AI_CREDIT_RATE_CREATED, "AiCreditRate", value.getId().toString());
        return RateView.from(value);
    }

    @Transactional
    public RateView replaceRate(UUID actor, String email, UUID id, RateInput input) {
        var slot = lockedRates(id);
        var previous = requireRate(slot, id);
        requireVersion(previous.getVersion(), input.version());
        if (previous.getPurpose() != input.purpose()
                || !Objects.equals(previous.getModelCategory(), category(input.modelCategory()))) {
            throw invalid("Tạo giá mới để đổi mục đích hoặc nhóm giá.");
        }
        var previousStatus = previous.getStatus();
        if (previousStatus == AiCreditRateStatus.ACTIVE) {
            archive(actor, email, previous);
        }
        // Never overwrite the price row referenced by historical reservations.
        var replacement = rates.saveAndFlush(AiCreditRate.create(previous.getPurpose(), previous.getModelCategory(),
                input.creditCost(), previousStatus, Instant.now()));
        audit.logAction(actor, email, AuditEventAction.AI_CREDIT_RATE_REPLACED,
                "AiCreditRate", replacement.getId().toString());
        return RateView.from(replacement);
    }

    @Transactional
    public RateView rateStatus(UUID actor, String email, UUID id, RateStatusInput input) {
        var slot = lockedRates(id);
        var selected = requireRate(slot, id);
        version(selected.getVersion(), input.version());
        if (selected.getStatus() == input.status()) {
            return RateView.from(selected);
        }
        if (input.status() == AiCreditRateStatus.ARCHIVED) {
            archive(actor, email, selected);
        } else {
            var current = slot.stream().filter(rate -> rate.getStatus() == AiCreditRateStatus.ACTIVE).findFirst();
            if (!Objects.equals(current.map(AiCreditRate::getId).orElse(null), input.replacesRateId())) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                        "Giá đang áp dụng đã thay đổi. Hãy tải lại trước khi kích hoạt.");
            }
            current.ifPresent(rate -> {
                requireVersion(rate.getVersion(), input.replacesRateVersion());
                archive(actor, email, rate);
            });
            // No future scheduling in this delivery: an activated price applies immediately.
            selected.activate(Instant.now());
            rates.saveAndFlush(selected);
            audit.logAction(actor, email, AuditEventAction.AI_CREDIT_RATE_ACTIVATED, "AiCreditRate", id.toString());
        }
        return RateView.from(selected);
    }

    private List<AiCreditRate> lockedRates(UUID id) {
        var slot = rates.findSlotById(id).orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy giá Credit."));
        return rates.lockSlot(slot.getPurpose(), slot.getModelCategory());
    }

    private AiCreditRate requireRate(List<AiCreditRate> slot, UUID id) {
        return slot.stream().filter(rate -> rate.getId().equals(id)).findFirst().orElseThrow(() ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy giá Credit."));
    }

    private CreditPackage requirePackage(UUID id) {
        return packages.findByIdForUpdate(id).orElseThrow(() ->
                new BusinessException(ErrorCode.CREDIT_PACKAGE_NOT_FOUND, "Không tìm thấy gói Credit."));
    }

    private void archive(UUID actor, String email, AiCreditRate value) {
        value.setStatus(AiCreditRateStatus.ARCHIVED);
        rates.saveAndFlush(value);
        audit.logAction(actor, email, AuditEventAction.AI_CREDIT_RATE_ARCHIVED, "AiCreditRate", value.getId().toString());
    }

    private void validatePackage(PackageInput input) {
        validatePrice(input.priceVnd());
        safeSum(input.baseCredits(), input.bonusCredits());
        if (input.name().trim().matches("(?s).*\\p{Cntrl}.*")) {
            throw invalid("Tên gói không được chứa ký tự điều khiển.");
        }
    }

    private void validatePrice(long price) {
        if (price < properties.getTopUp().getMinVnd() || price > properties.getTopUp().getMaxVnd()) {
            throw invalid("Giá gói nằm ngoài giới hạn nạp đang cấu hình.");
        }
    }

    private void requireVersion(long actual, Long expected) {
        if (expected == null) {
            throw invalid("Thiếu phiên bản dữ liệu.");
        }
        version(actual, expected);
    }

    private Pageable bounded(Pageable page) {
        return PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
