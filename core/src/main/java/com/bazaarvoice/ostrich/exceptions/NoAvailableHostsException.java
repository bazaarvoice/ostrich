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

/**
 * An exception indicating that {@link com.bazaarvoice.ostrich.HostDiscovery} provided no end points.
 */
public class NoAvailableHostsException extends DiscoveryException {
    private static final long serialVersionUID = 0;

    public NoAvailableHostsException() {
        super();
    }

    public NoAvailableHostsException(String message) {
        super(message);
    }

    public NoAvailableHostsException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoAvailableHostsException(Throwable cause) {
        super(cause);
    }
}
