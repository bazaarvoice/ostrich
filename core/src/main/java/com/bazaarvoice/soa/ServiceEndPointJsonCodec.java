package com.bazaarvoice.soa;

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
