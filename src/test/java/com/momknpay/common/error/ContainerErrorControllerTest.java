package com.momknpay.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ContainerErrorControllerTest {

    private final ContainerErrorController controller = new ContainerErrorController();

    @Test
    void containerErrorsUseTheEnvelope() {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);

        var response = controller.error(request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().error().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void unknownStatusFallsBackToInternalError() {
        var response = controller.error(new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
    }
}
