package com.momknpay.catalog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link ServiceCategory} as its lowercase wire value. */
@Converter(autoApply = true)
public class ServiceCategoryConverter implements AttributeConverter<ServiceCategory, String> {

    @Override
    public String convertToDatabaseColumn(ServiceCategory category) {
        return category == null ? null : category.wire();
    }

    @Override
    public ServiceCategory convertToEntityAttribute(String value) {
        return value == null ? null : ServiceCategory.fromWire(value);
    }
}
