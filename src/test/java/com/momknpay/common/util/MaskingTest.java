package com.momknpay.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.momknpay.catalog.domain.ServiceCategory;
import com.momknpay.catalog.domain.ServiceCategoryConverter;
import org.junit.jupiter.api.Test;

class MaskingTest {

    @Test
    void onlyTheLastFourDigitsStayVisible() {
        assertThat(Masking.subscriber("1024750891")).isEqualTo("******0891");
        assertThat(Masking.subscriber("01050000002")).isEqualTo("*******0002");
    }

    @Test
    void shortOrMissingValuesRevealNothing() {
        assertThat(Masking.subscriber("1234")).isEqualTo("****");
        assertThat(Masking.subscriber("")).isEmpty();
        assertThat(Masking.subscriber(null)).isNull();
    }

    @Test
    void categoriesAreLowercaseOnTheWireAndInTheDatabase() {
        var converter = new ServiceCategoryConverter();

        assertThat(ServiceCategory.ELECTRICITY.wire()).isEqualTo("electricity");
        assertThat(ServiceCategory.fromWire("landline")).isEqualTo(ServiceCategory.LANDLINE);
        assertThat(converter.convertToDatabaseColumn(ServiceCategory.GAS)).isEqualTo("gas");
        assertThat(converter.convertToEntityAttribute("water")).isEqualTo(ServiceCategory.WATER);
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }
}
