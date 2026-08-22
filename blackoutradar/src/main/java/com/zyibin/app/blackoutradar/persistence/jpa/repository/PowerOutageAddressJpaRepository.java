package com.zyibin.app.blackoutradar.persistence.jpa.repository;

import com.zyibin.app.blackoutradar.persistence.jpa.entity.PowerOutageAddressEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PowerOutageAddressJpaRepository extends JpaRepository<PowerOutageAddressEntity, UUID> {

    List<PowerOutageAddressEntity> findAllByPowerOutageId(UUID powerOutageId);

    @Modifying
    @Query("delete from PowerOutageAddressEntity a where a.powerOutage.id = :powerOutageId")
    void deleteByPowerOutageId(@Param("powerOutageId") UUID powerOutageId);
}