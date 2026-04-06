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
package org.jwcarman.odyssey.spi;

import java.util.stream.Stream;
import org.jwcarman.odyssey.core.OdysseyEvent;

public interface OdysseyEventLog {

  String ephemeralKey();

  String channelKey(String name);

  String broadcastKey(String name);

  String append(String streamKey, OdysseyEvent event);

  Stream<OdysseyEvent> readAfter(String streamKey, String lastId);

  Stream<OdysseyEvent> readLast(String streamKey, int count);

  void delete(String streamKey);
}
