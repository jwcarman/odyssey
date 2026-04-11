/*
 * Copyright © 2026 James Carman
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
package org.jwcarman.odyssey.core;

import java.util.function.Consumer;

/**
 * Per-call customizer type for {@link Odyssey#publisher(String, Class, PublisherCustomizer)}.
 * Extends {@link Consumer Consumer&lt;PublisherConfig&gt;} so every existing lambda shape ({@code
 * cfg -> cfg.ttl(...)}) still compiles untouched; the named type exists purely to make the method
 * signature self-documenting.
 *
 * <p>Odyssey does not treat {@code PublisherCustomizer} beans specially. If you want every
 * publisher in your app to pick up a common policy, either set {@code odyssey.default-ttl.*} in
 * properties or wrap the shared logic in a helper method your code calls at every publisher
 * construction site.
 */
@FunctionalInterface
public interface PublisherCustomizer extends Consumer<PublisherConfig> {}
