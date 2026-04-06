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

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record OdysseyEvent(
    String id,
    String streamKey,
    String eventType,
    String payload,
    Instant timestamp,
    Map<String, String> metadata) {

  public OdysseyEvent {
    metadata =
        metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String id;
    private String streamKey;
    private String eventType;
    private String payload;
    private Instant timestamp;
    private Map<String, String> metadata = Map.of();

    private Builder() {}

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder streamKey(String streamKey) {
      this.streamKey = streamKey;
      return this;
    }

    public Builder eventType(String eventType) {
      this.eventType = eventType;
      return this;
    }

    public Builder payload(String payload) {
      this.payload = payload;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder metadata(Map<String, String> metadata) {
      this.metadata = metadata;
      return this;
    }

    public OdysseyEvent build() {
      return new OdysseyEvent(id, streamKey, eventType, payload, timestamp, metadata);
    }
  }
}
