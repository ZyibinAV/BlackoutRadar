package com.zyibin.app.blackoutradar.application.notification;

import com.zyibin.app.blackoutradar.domain.notification.Notification;
import com.zyibin.app.blackoutradar.domain.notification.NotificationDelivery;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completes one delivery and finalizes its notification in a single short
 * database transaction (see ADR-014):
 *
 * <pre>
 * fenced update NotificationDelivery
 *   -&gt; if ownership confirmed
 * lock Notification
 *   -&gt; re-read current NotificationDelivery states
 *   -&gt; calculate final Notification state
 *   -&gt; update Notification
 * </pre>
 *
 * <p>If the transaction rolls back, neither the terminal delivery state nor
 * the finalization is committed. The external delivery itself always runs
 * outside of this transaction.
 */
@Service
public class DeliveryCompletionService {

    private final NotificationDeliveryFencingPort fencingPort;
    private final NotificationFinalizationService finalizationService;

    public DeliveryCompletionService(NotificationDeliveryFencingPort fencingPort,
                                     NotificationFinalizationService finalizationService) {
        this.fencingPort = Objects.requireNonNull(fencingPort, "fencingPort must not be null");
        this.finalizationService =
                Objects.requireNonNull(finalizationService, "finalizationService must not be null");
    }

    @Transactional
    public Optional<Notification> complete(UUID deliveryId, UUID ownershipToken,
                                           NotificationDelivery newState) {
        Objects.requireNonNull(deliveryId, "deliveryId must not be null");
        Objects.requireNonNull(ownershipToken, "ownershipToken must not be null");
        Objects.requireNonNull(newState, "newState must not be null");
        Optional<NotificationDelivery> saved =
                fencingPort.saveIfOwned(deliveryId, ownershipToken, newState);
        if (saved.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(finalizationService.finalizeNotification(saved.get().notification().id()));
    }
}
