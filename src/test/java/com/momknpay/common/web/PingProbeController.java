package com.momknpay.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint under {@code /v1} for header and logging checks. */
@RestController
class PingProbeController {

    private static final Logger log = LoggerFactory.getLogger(PingProbeController.class);

    @GetMapping("/test/ping")
    String ping() {
        log.info("probe.ping");
        return "pong";
    }
}
