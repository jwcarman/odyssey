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
package org.jwcarman.odyssey.example;

import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.springframework.stereotype.Component;

/**
 * Typed factory for this app's streams. Centralizes the name conventions and TTL policies so
 * controllers never touch strings or TTL values directly.
 */
@Component
public class Streams {

  private final Odyssey odyssey;

  public Streams(Odyssey odyssey) {
    this.odyssey = odyssey;
  }

  public OdysseyStream<BroadcastController.Announcement> announcements() {
    return odyssey.stream(
        "announcements", BroadcastController.Announcement.class, TtlPolicies.BROADCAST);
  }

  public OdysseyStream<NotifyController.Notification> userChannel(String userId) {
    return odyssey.stream(
        "user:" + userId, NotifyController.Notification.class, TtlPolicies.CHANNEL);
  }

  public OdysseyStream<TaskController.TaskProgress> taskProgress(String taskId) {
    return odyssey.stream(taskId, TaskController.TaskProgress.class, TtlPolicies.EPHEMERAL);
  }
}
