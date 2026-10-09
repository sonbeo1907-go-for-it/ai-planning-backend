package com.codegym.aiplanning.repository.billing;

import com.codegym.aiplanning.entity.billing.CreditPackage;
import com.codegym.aiplanning.entity.billing.CreditPackageStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditPackageRepository extends JpaRepository<CreditPackage, UUID> {

    List<CreditPackage> findByStatusOrderBySortOrderAsc(CreditPackageStatus status);

    Optional<CreditPackage> findByPackageCode(String packageCode);
}
