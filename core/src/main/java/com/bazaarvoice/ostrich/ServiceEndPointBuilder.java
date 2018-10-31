package com.bazaarvoice.ostrich;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.Objects;

public class ServiceEndPointBuilder {
    // Service names and versions have a restricted set of valid characters in them for simplicity.  These are the
    // characters that can appear in a URL without needing escaping.  This will let us refer to services with a URL
    // looking structure (e.g. prod://services/profile-v1)
    private static final CharMatcher VALID_CHARACTERS = CharMatcher.none()
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.anyOf("._-:"))
            .precomputed();

    private String _serviceName = null;
    private String _id = null;
    private String _payload = null;

    public ServiceEndPointBuilder withServiceName(String serviceName) {
        Preconditions.checkArgument(getCheckExpression(serviceName));

        _serviceName = serviceName;
        return this;
    }

    private static boolean getCheckExpression(String argument) {
        return !Strings.isNullOrEmpty(argument) && VALID_CHARACTERS.matchesAllOf(argument);
    }

    public ServiceEndPointBuilder withId(String id) {
        Preconditions.checkArgument(getCheckExpression(id));

        _id = id;
        return this;
    }

    public ServiceEndPointBuilder withPayload(String payload) {
        _payload = payload;
        return this;
    }

    public ServiceEndPoint build() {
        Preconditions.checkState(getCheckExpression(_serviceName));
        final String serviceName = _serviceName;

        Preconditions.checkState(getCheckExpression(_id));
        final String id = _id;

        final String payload = _payload;

        return new ServiceEndPoint() {
            @Override
            public String getServiceName() {
                return serviceName;
            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getPayload() {
                return payload;
            }

            @Override
            public int hashCode() {
                return Objects.hash(serviceName, id);
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof ServiceEndPoint)) return false;

                ServiceEndPoint that = (ServiceEndPoint) obj;
                return Objects.equals(serviceName, that.getServiceName())
                        && Objects.equals(id, that.getId())
                        && Objects.equals(payload, that.getPayload());
            }

            @Override
            public String toString() {
                return com.google.common.base.Objects.toStringHelper("ServiceEndPoint")
                        .add("name", serviceName)
                        .add("id", id)
                        .add("payload", payload)
                        .toString();
            }
        };
    }
}
