package com.momknpay.user.web;

import com.momknpay.common.web.CurrentUser;
import com.momknpay.common.web.UserRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint proving {@code @CurrentUser} is wired into Spring MVC. */
@RestController
class WhoAmIProbeController {

    @GetMapping("/test/whoami")
    UserRef whoAmI(@CurrentUser UserRef user) {
        return user;
    }
}
