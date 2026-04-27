package com.paymentplatform.notificationservice.application.mapper;

import com.paymentplatform.commonlib.dto.NotificationDto;
import com.paymentplatform.notificationservice.domain.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper converting {@link Notification} JPA entities to
 * {@link com.paymentplatform.commonlib.dto.NotificationDto} API objects.
 *
 * <p>The only non-default mapping is {@code id} → {@code notificationId}
 * (the DTO uses a descriptive field name instead of the generic {@code id}).</p>
 */
@Mapper(componentModel = "spring")
public interface NotificationMapper {

    /**
     * Maps a {@link Notification} entity to its DTO.
     *
     * @param notification the notification entity
     * @return populated {@link com.paymentplatform.commonlib.dto.NotificationDto}
     */
    @Mapping(target = "notificationId", source = "id")
    NotificationDto toDto(Notification notification);
}
