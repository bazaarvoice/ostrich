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

import com.google.common.base.CharMatcher;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;

public class ServiceEndPointBuilder {
    // Service names and versions have a restricted set of valid characters in them for simplicity.  These are the
    // characters that can appear in a URL without needing escaping.  This will let us refer to services with a URL
    // looking structure (e.g. prod://services/profile-v1)
    private static final CharMatcher VALID_CHARACTERS = CharMatcher.NONE
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9'))
            .or(CharMatcher.anyOf("._-:"))
            .precomputed();

    private Optional<String> _serviceName = Optional.absent();
    private Optional<String> _id = Optional.absent();
    private Optional<String> _payload = Optional.absent();

    public ServiceEndPointBuilder withServiceName(String serviceName) {
        checkArgument(!Strings.isNullOrEmpty(serviceName) && VALID_CHARACTERS.matchesAllOf(serviceName));

        _serviceName = Optional.of(serviceName);
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
        final String serviceName = _serviceName.get();
        final String id = _id.get();
        final String payload = _payload.orNull();

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
                return Objects.hashCode(serviceName, id);
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (!(obj instanceof ServiceEndPoint)) return false;

                ServiceEndPoint that = (ServiceEndPoint) obj;
                return Objects.equal(serviceName, that.getServiceName())
                        && Objects.equal(id, that.getId())
                        && Objects.equal(payload, that.getPayload());
            }

            @Override
            public String toString() {
                return Objects.toStringHelper("ServiceEndPoint")
                        .add("name", serviceName)
                        .add("id", id)
                        .toString();
            }
        };
    }
}
