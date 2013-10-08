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
package com.bazaarvoice.ostrich.exceptions;

public abstract class DiscoveryException extends ServiceException {
    private static final long serialVersionUID = 0;

    protected DiscoveryException() {
        super();
    }

    protected DiscoveryException(String message) {
        super(message);
    }

    protected DiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }

    protected DiscoveryException(Throwable cause) {
        super(cause);
    }
}
