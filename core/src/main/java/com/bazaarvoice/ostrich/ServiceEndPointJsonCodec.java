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

import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ServiceEndPointJsonCodec {
    public static String toJson(ServiceEndPoint endPoint, Map<String, Object> extras) {
        Map<String, Object> data = Maps.newLinkedHashMap(extras);
        data.put("name", endPoint.getServiceName());
        data.put("id", endPoint.getId());
        data.put("payload", endPoint.getPayload());
        return JsonHelper.toJson(data);
    }

    public static String toJson(ServiceEndPoint endPoint) {
        return toJson(endPoint, Collections.<String, Object>emptyMap());
    }

    public static ServiceEndPoint fromJson(String json) {
        Map<?, ?> data = JsonHelper.fromJson(json, Map.class);
        String name = (String) checkNotNull(data.get("name"));
        String id = (String) checkNotNull(data.get("id"));
        String payload = (String) data.get("payload");

        return new ServiceEndPointBuilder()
                .withServiceName(name)
                .withId(id)
                .withPayload(payload)
                .build();
    }

    // Private, not instantiable.
    private ServiceEndPointJsonCodec() {
    }
}
