package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.SourceEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceJpaRepository extends JpaRepository<SourceEntity, UUID> {
}