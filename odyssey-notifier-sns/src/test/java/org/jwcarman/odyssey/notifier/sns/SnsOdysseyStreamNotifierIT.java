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
package org.jwcarman.odyssey.notifier.sns;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Testcontainers
class SnsOdysseyStreamNotifierIT {

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
          .withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);

  private SnsClient snsClient;
  private SqsClient sqsClient;
  private String topicArn;
  private SnsOdysseyStreamNotifier notifier;

  @BeforeEach
  void setUp() {
    URI endpoint = localstack.getEndpointOverride(LocalStackContainer.Service.SNS);
    StaticCredentialsProvider credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
    Region region = Region.of(localstack.getRegion());

    snsClient =
        SnsClient.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(credentials)
            .region(region)
            .build();

    sqsClient =
        SqsClient.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(credentials)
            .region(region)
            .build();

    topicArn = snsClient.createTopic(request -> request.name("odyssey-notifications")).topicArn();

    notifier = new SnsOdysseyStreamNotifier(snsClient, sqsClient, topicArn, 300);
  }

  @AfterEach
  void tearDown() {
    if (notifier != null && notifier.isRunning()) {
      notifier.stop();
    }
    if (snsClient != null) {
      snsClient.close();
    }
    if (sqsClient != null) {
      sqsClient.close();
    }
  }

  @Test
  void notifyAndSubscribeRoundTrip() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> receivedStreamKeys = new CopyOnWriteArrayList<>();
    List<String> receivedEventIds = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          receivedStreamKeys.add(streamKey);
          receivedEventIds.add(eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:channel:test", "100-0");

    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(receivedStreamKeys).containsExactly("odyssey:channel:test");
    assertThat(receivedEventIds).containsExactly("100-0");
  }

  @Test
  void streamKeyIsCorrectlyExtractedFromMessage() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> receivedStreamKeys = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          receivedStreamKeys.add(streamKey);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:ephemeral:abc-123", "1-0");

    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(receivedStreamKeys).containsExactly("odyssey:ephemeral:abc-123");
  }

  @Test
  void multipleHandlersAllReceiveNotifications() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    List<String> handler1Received = new CopyOnWriteArrayList<>();
    List<String> handler2Received = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          handler1Received.add(eventId);
          latch.countDown();
        });
    notifier.subscribe(
        (streamKey, eventId) -> {
          handler2Received.add(eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:broadcast:news", "5-0");

    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(handler1Received).containsExactly("5-0");
    assertThat(handler2Received).containsExactly("5-0");
  }

  @Test
  void multipleNotificationsAreDelivered() throws Exception {
    CountDownLatch latch = new CountDownLatch(3);
    List<String> receivedEventIds = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          receivedEventIds.add(eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:channel:test", "1-0");
    notifier.notify("odyssey:channel:test", "2-0");
    notifier.notify("odyssey:channel:test", "3-0");

    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(receivedEventIds).containsExactlyInAnyOrder("1-0", "2-0", "3-0");
  }

  @Test
  void stopPreventsNewNotifications() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    List<String> receivedEventIds = new CopyOnWriteArrayList<>();

    notifier.subscribe(
        (streamKey, eventId) -> {
          receivedEventIds.add(eventId);
          latch.countDown();
        });
    notifier.start();

    notifier.notify("odyssey:channel:test", "1-0");
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    notifier.stop();
    notifier.notify("odyssey:channel:test", "2-0");

    Thread.sleep(2000);
    assertThat(receivedEventIds).containsExactly("1-0");
  }

  @Test
  void temporaryQueueIsCleanedUpOnStop() {
    notifier.subscribe((streamKey, eventId) -> {});
    notifier.start();

    // Verify queue exists
    int queueCountBefore = sqsClient.listQueues().queueUrls().size();
    assertThat(queueCountBefore).isGreaterThanOrEqualTo(1);

    notifier.stop();

    // Verify queue is deleted
    int queueCountAfter = sqsClient.listQueues().queueUrls().size();
    assertThat(queueCountAfter).isLessThan(queueCountBefore);
  }
}
