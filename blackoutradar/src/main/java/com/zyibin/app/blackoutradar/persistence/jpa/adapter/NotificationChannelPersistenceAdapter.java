package com.zyibin.app.blackoutradar.persistence.jpa.adapter;

import com.zyibin.app.blackoutradar.domain.notification.NotificationChannel;
import com.zyibin.app.blackoutradar.domain.notification.port.NotificationChannelPort;
import com.zyibin.app.blackoutradar.persistence.jpa.entity.NotificationChannelEntity;
import com.zyibin.app.blackoutradar.persistence.jpa.mapper.NotificationChannelMapper;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.NotificationChannelJpaRepository;
import com.zyibin.app.blackoutradar.persistence.jpa.repository.UserJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationChannelPersistenceAdapter implements NotificationChannelPort {

    private final NotificationChannelJpaRepository repository;
    private final NotificationChannelMapper mapper;
    private final UserJpaRepository userRepository;

    public NotificationChannelPersistenceAdapter(NotificationChannelJpaRepository repository,
                                                 NotificationChannelMapper mapper,
                                                 UserJpaRepository userRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationChannel> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationChannel> findByUserId(UUID userId) {
        return repository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public NotificationChannel save(NotificationChannel channel) {
        NotificationChannelEntity entity = mapper.toEntity(channel);
        entity.setUser(userRepository.getReferenceById(channel.user().id()));
        NotificationChannelEntity saved = repository.save(entity);
        repository.flush();
        return mapper.toDomain(saved);
    }
}
