/*
 * Copyright 2013 Bazaarvoice, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bazaarvoice.ostrich.examples.calculator.service;

import com.yammer.metrics.core.HealthCheck;

import javax.ws.rs.core.Response;

public class CalculatorHealthCheck extends HealthCheck {
    protected CalculatorHealthCheck() {
        super("calculator");
    }

    @Override
    protected Result check() throws Exception {
        if (CalculatorService.STATUS_OVERRIDE == Response.Status.OK) {
            return Result.healthy();
        } else {
            return Result.unhealthy("unhealthy by toggle");
        }
    }
}
