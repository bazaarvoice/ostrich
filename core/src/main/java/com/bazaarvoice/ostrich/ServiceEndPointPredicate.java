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

/**
 * A predicate interface for {@link ServiceEndPoint} instances.
 * <p/>
 * NOTE: This interface could obviously be replaced by a Guava Predicate, but the goal is to not include any
 * 3rd party library classes in the public interface of Ostrich so that's not acceptable.
 */
public interface ServiceEndPointPredicate {
    boolean apply(ServiceEndPoint endPoint);
}
