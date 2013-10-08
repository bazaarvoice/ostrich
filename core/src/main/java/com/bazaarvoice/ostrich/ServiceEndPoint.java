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
package com.bazaarvoice.ostrich;

public interface ServiceEndPoint {
    /** The name of the service. */
    String getServiceName();

    /**
     * An opaque identifier for this end point.
     * <p/>
     * The format of this identifier and information (if any) contained within it is application specific.  Ostrich
     * does not introspect into this at all.
     */
    String getId();

    /** An optional payload provided by the user that registered the service. */
    String getPayload();
}
