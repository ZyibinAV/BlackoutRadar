package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.ExternalIdentityEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentityEntity, UUID> {

    Optional<ExternalIdentityEntity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
