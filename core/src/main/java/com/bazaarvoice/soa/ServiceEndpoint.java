package com.bazaarvoice.soa;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.net.HostAndPort;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class ServiceEndpoint {
    // Service names and versions have a restricted set of valid characters in them for simplicity.  These are the
    // characters that can appear in a URL without needing escaping.  This will let us refer to services with a URL
    // looking structure (e.g. prod://services/profile-v1)
    private static final CharMatcher VALID_CHARACTERS = CharMatcher.NONE
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.anyOf("._-"))
            .precomputed();

    // All dates are represented in ISO-8601 format and in the UTC time zone.
    @VisibleForTesting
    static final DateTimeFormatter ISO8601 = ISODateTimeFormat.dateTime().withZoneUTC();

    private final String _serviceName;
    private final HostAndPort _address;
    private final DateTime _registrationTime;
    private final String _payload;

    public ServiceEndpoint(String serviceName, String hostname, int port) {
        this(serviceName, hostname, port, DateTime.now(), null);
    }

    public ServiceEndpoint(String serviceName, String hostname, int port, String payload) {
        this(serviceName, hostname, port, DateTime.now(), payload);
    }

    @VisibleForTesting
    ServiceEndpoint(String serviceName, String hostname, int port, DateTime registrationTime, String payload) {
        checkArgument(serviceName != null && serviceName.length() > 0);
        checkArgument(VALID_CHARACTERS.matchesAllOf(serviceName));
        checkArgument(hostname != null && hostname.length() > 0);

        _serviceName = serviceName;
        _address = HostAndPort.fromParts(hostname, port);
        _registrationTime = registrationTime.toDateTime(DateTimeZone.UTC);
        _payload = payload;
    }

    public String getServiceName() {
        return _serviceName;
    }

    public String getHostname() {
        return _address.getHostText();
    }

    public int getPort() {
        return _address.getPort();
    }

    public String getServiceAddress() {
        return _address.toString();
    }

    public DateTime getRegistrationTime() {
        return _registrationTime;
    }

    public String getPayload() {
        return _payload;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_serviceName, _address, _payload);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ServiceEndpoint)) return false;

        ServiceEndpoint that = (ServiceEndpoint) obj;
        return Objects.equal(_serviceName, that._serviceName)
                && Objects.equal(_address, that._address)
                && Objects.equal(_payload, that._payload);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", _serviceName)
                .add("address", _address)
                .toString();
    }

    public String toJson() {
        Map<String, Object> data = Maps.newLinkedHashMap();
        data.put("registration-time", ISO8601.print(_registrationTime));
        data.put("name", _serviceName);
        data.put("host", _address.getHostText());
        data.put("port", _address.getPort());
        data.put("payload", _payload);
        return JsonHelper.toJson(data);
    }

    public static ServiceEndpoint fromJson(String json) {
        Map<?, ?> data = JsonHelper.fromJson(json, Map.class);
        String name = (String) checkNotNull(data.get("name"));
        String host = (String) checkNotNull(data.get("host"));
        int port = ((Number) checkNotNull(data.get("port"))).intValue();
        DateTime registrationTime = ISO8601.parseDateTime((String) checkNotNull(data.get("registration-time")));
        String payload = (String) data.get("payload");

        return new ServiceEndpoint(name, host, port, registrationTime, payload);
    }
}
