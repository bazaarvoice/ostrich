package com.bazaarvoice.soa;

import com.google.common.base.CharMatcher;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;

public class ServiceEndPointBuilder {
    // Service types etc. have a restricted set of valid characters in them for simplicity.  These are the characters
    // that can appear in a URL without needing escaping.  This will let us refer to services with a URL looking
    // structure (e.g. prod://services/profile-v1)
    private static final CharMatcher VALID_CHARACTERS = CharMatcher.NONE
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.anyOf("._-:"))
            .precomputed();

    private Optional<String> _ensembleName = Optional.absent();
    private Optional<String> _serviceType = Optional.absent();
    private Optional<String> _id = Optional.absent();
    private Optional<String> _payload = Optional.absent();

    public ServiceEndPointBuilder withEnsembleName(String ensembleName) {
        if (Strings.isNullOrEmpty(ensembleName)) {
            _ensembleName = Optional.absent();
        } else {
            checkArgument(VALID_CHARACTERS.matchesAllOf(ensembleName));
            _ensembleName = Optional.of(ensembleName);
        }
        return this;
    }

    public ServiceEndPointBuilder withServiceType(String serviceType) {
        checkArgument(!Strings.isNullOrEmpty(serviceType) && VALID_CHARACTERS.matchesAllOf(serviceType));

        _serviceType = Optional.of(serviceType);
        return this;
    }

    public ServiceEndPointBuilder withId(String id) {
        checkArgument(!Strings.isNullOrEmpty(id) && VALID_CHARACTERS.matchesAllOf(id));

        _id = Optional.of(id);
        return this;
    }

    public ServiceEndPointBuilder withPayload(String payload) {
        _payload = Optional.fromNullable(payload);
        return this;
    }

    public ServiceEndPoint build() {
        final String ensembleName = _ensembleName.orNull();
        final String serviceType = _serviceType.get();
        final String id = _id.get();
        final String payload = _payload.orNull();

        return new ServiceEndPoint() {
            @Override
            public String getEnsembleName() {
                return ensembleName;
            }

            @Override
            public String getServiceType() {
                return serviceType;
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
                return Objects.hashCode(ensembleName, serviceType, id);
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof ServiceEndPoint)) return false;

                ServiceEndPoint that = (ServiceEndPoint) obj;
                return Objects.equal(ensembleName, that.getEnsembleName())
                        && Objects.equal(serviceType, that.getServiceType())
                        && Objects.equal(id, that.getId())
                        && Objects.equal(payload, that.getPayload());
            }

            @Override
            public String toString() {
                return Objects.toStringHelper("ServiceEndPoint")
                        .add("ensemble", ensembleName)
                        .add("type", serviceType)
                        .add("id", id)
                        .toString();
            }
        };
    }
}
