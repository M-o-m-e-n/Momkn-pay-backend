package com.momknpay.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the housekeeping jobs (LLD §4.3). Every job must be idempotent. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
