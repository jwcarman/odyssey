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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;

@ExtendWith(MockitoExtension.class)
class SnsOdysseyStreamNotifierTest {

  private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:odyssey-topic";

  @Mock private SnsClient snsClient;

  @Mock private SqsClient sqsClient;

  private SnsOdysseyStreamNotifier notifier;

  @BeforeEach
  void setUp() {
    notifier = new SnsOdysseyStreamNotifier(snsClient, sqsClient, TOPIC_ARN, 60);
  }

  @Test
  void parseAndDispatchWithValidPayload() {
    List<String> keys = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    notifier.subscribe(
        (streamKey, eventId) -> {
          keys.add(streamKey);
          ids.add(eventId);
        });

    notifier.parseAndDispatch("stream:test|42");

    assertEquals(1, keys.size());
    assertEquals("stream:test", keys.getFirst());
    assertEquals("42", ids.getFirst());
  }

  @Test
  void parseAndDispatchWithMalformedPayload() {
    List<String> received = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> received.add(streamKey));

    notifier.parseAndDispatch("no-delimiter-here");

    assertTrue(received.isEmpty());
  }

  @Test
  void extractSnsMessageWithSnsEnvelope() {
    String body =
        "{\"Type\":\"Notification\",\"MessageId\":\"abc-123\",\"TopicArn\":\""
            + TOPIC_ARN
            + "\",\"Message\":\"stream:test|42\",\"Timestamp\":\"2026-04-06T00:00:00Z\"}";

    String result = notifier.extractSnsMessage(body);

    assertEquals("stream:test|42", result);
  }

  @Test
  void extractSnsMessageWithRawBody() {
    String body = "stream:test|42";

    String result = notifier.extractSnsMessage(body);

    assertEquals("stream:test|42", result);
  }

  @Test
  void extractSnsMessageWithEscapedCharacters() {
    String body = "{\"Message\":\"stream:test\\\"quoted\\\"|42\",\"Type\":\"Notification\"}";

    String result = notifier.extractSnsMessage(body);

    assertEquals("stream:test\"quoted\"|42", result);
  }

  @Test
  void subscribeAddsHandler() {
    List<String> received = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> received.add(streamKey + "|" + eventId));

    notifier.parseAndDispatch("my:stream|99");

    assertEquals(1, received.size());
    assertEquals("my:stream|99", received.getFirst());
  }

  @Test
  void notifyPublishesToSns() {
    notifier.notify("key", "id");

    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());

    PublishRequest request = captor.getValue();
    assertEquals(TOPIC_ARN, request.topicArn());
    assertEquals("key|id", request.message());
    assertTrue(request.messageAttributes().containsKey("streamKey"));
    assertEquals("key", request.messageAttributes().get("streamKey").stringValue());
  }

  @Test
  void multipleHandlersAllReceiveNotification() {
    List<String> handler1 = new ArrayList<>();
    List<String> handler2 = new ArrayList<>();
    notifier.subscribe((streamKey, eventId) -> handler1.add(eventId));
    notifier.subscribe((streamKey, eventId) -> handler2.add(eventId));

    notifier.parseAndDispatch("stream:test|42");

    assertEquals(1, handler1.size());
    assertEquals("42", handler1.getFirst());
    assertEquals(1, handler2.size());
    assertEquals("42", handler2.getFirst());
  }

  @Test
  void parseAndDispatchWithSnsWrappedPayload() {
    List<String> keys = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    notifier.subscribe(
        (streamKey, eventId) -> {
          keys.add(streamKey);
          ids.add(eventId);
        });

    String snsEnvelope =
        "{\"Type\":\"Notification\",\"MessageId\":\"msg-001\",\"TopicArn\":\""
            + TOPIC_ARN
            + "\",\"Message\":\"odyssey:channel:alpha|7-0\",\"Timestamp\":\"2026-04-06T12:00:00Z\"}";

    notifier.parseAndDispatch(snsEnvelope);

    assertEquals(1, keys.size());
    assertEquals("odyssey:channel:alpha", keys.getFirst());
    assertEquals("7-0", ids.getFirst());
  }

  @Test
  void stopCleansUpSubscriptionAndQueue() throws Exception {
    // Set subscriptionArn and queueUrl via reflection so we can test stop() in isolation.
    Field subArnField = SnsOdysseyStreamNotifier.class.getDeclaredField("subscriptionArn");
    subArnField.setAccessible(true);
    subArnField.set(notifier, "arn:aws:sns:us-east-1:123456789012:sub-001");

    Field queueUrlField = SnsOdysseyStreamNotifier.class.getDeclaredField("queueUrl");
    queueUrlField.setAccessible(true);
    queueUrlField.set(notifier, "https://sqs.us-east-1.amazonaws.com/123456789012/odyssey-test");

    notifier.stop();

    verify(snsClient).unsubscribe(any(UnsubscribeRequest.class));
    verify(sqsClient).deleteQueue(any(DeleteQueueRequest.class));
  }

  @Test
  void stopHandlesUnsubscribeFailure() throws Exception {
    Field subArnField = SnsOdysseyStreamNotifier.class.getDeclaredField("subscriptionArn");
    subArnField.setAccessible(true);
    subArnField.set(notifier, "arn:aws:sns:us-east-1:123456789012:sub-001");

    Field queueUrlField = SnsOdysseyStreamNotifier.class.getDeclaredField("queueUrl");
    queueUrlField.setAccessible(true);
    queueUrlField.set(notifier, "https://sqs.us-east-1.amazonaws.com/123456789012/odyssey-test");

    doThrow(new RuntimeException("unsubscribe failed"))
        .when(snsClient)
        .unsubscribe(any(UnsubscribeRequest.class));

    assertDoesNotThrow((org.junit.jupiter.api.function.Executable) notifier::stop);
    // deleteQueue should still be called even though unsubscribe threw
    verify(sqsClient).deleteQueue(any(DeleteQueueRequest.class));
  }

  @Test
  void stopHandlesDeleteQueueFailure() throws Exception {
    Field subArnField = SnsOdysseyStreamNotifier.class.getDeclaredField("subscriptionArn");
    subArnField.setAccessible(true);
    subArnField.set(notifier, "arn:aws:sns:us-east-1:123456789012:sub-001");

    Field queueUrlField = SnsOdysseyStreamNotifier.class.getDeclaredField("queueUrl");
    queueUrlField.setAccessible(true);
    queueUrlField.set(notifier, "https://sqs.us-east-1.amazonaws.com/123456789012/odyssey-test");

    doThrow(new RuntimeException("deleteQueue failed"))
        .when(sqsClient)
        .deleteQueue(any(DeleteQueueRequest.class));

    assertDoesNotThrow((org.junit.jupiter.api.function.Executable) notifier::stop);
  }

  @Test
  void extractSnsMessageNoColonAfterMessageKey() {
    // "Message" key is present but there is no colon following it
    String body = "{\"Message\" no-colon \"value\"}";
    assertEquals(body, notifier.extractSnsMessage(body));
  }

  @Test
  void extractSnsMessageNoOpenQuote() {
    // colon present but no opening quote for the value
    String body = "{\"Message\": no-quote-here}";
    assertEquals(body, notifier.extractSnsMessage(body));
  }

  @Test
  void extractSnsMessageNoClosingQuote() {
    // opening quote present but string is never closed
    String body = "{\"Message\": \"unterminated value";
    assertEquals(body, notifier.extractSnsMessage(body));
  }

  @Test
  void findClosingQuoteReturnsNegativeOneWhenNoClose() {
    String s = "no closing quote here";
    assertEquals(-1, notifier.findClosingQuote(s, 0));
  }

  @Test
  void unescapeWithNoBackslash() {
    String s = "hello world";
    assertSame(s, notifier.unescape(s));
  }

  @Test
  void parseAndDispatchWithEmptyHandlerList() {
    // No handlers registered, malformed payload — should not throw
    assertDoesNotThrow(() -> notifier.parseAndDispatch("malformed-no-delimiter"));
  }
}
