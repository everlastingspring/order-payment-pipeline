package com.paymentplatform.notificationservice.application.mapper;

import com.paymentplatform.commonlib.dto.NotificationDto;
import com.paymentplatform.notificationservice.domain.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "notificationId", source = "id")
    NotificationDto toDto(Notification notification);
}
