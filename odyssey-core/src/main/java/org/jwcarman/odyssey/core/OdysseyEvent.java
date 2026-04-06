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
