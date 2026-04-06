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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jwcarman.odyssey.spi.NotificationHandler;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

public class SnsOdysseyStreamNotifier implements OdysseyStreamNotifier, SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(SnsOdysseyStreamNotifier.class);
  private static final String STREAM_KEY_ATTRIBUTE = "streamKey";
  private static final String DELIMITER = "|";

  private final SnsClient snsClient;
  private final SqsClient sqsClient;
  private final String topicArn;
  private final List<NotificationHandler> handlers = new CopyOnWriteArrayList<>();

  private volatile boolean running;
  private volatile String queueUrl;
  private volatile String queueArn;
  private volatile String subscriptionArn;
  private volatile Thread pollerThread;

  public SnsOdysseyStreamNotifier(SnsClient snsClient, SqsClient sqsClient, String topicArn) {
    this.snsClient = snsClient;
    this.sqsClient = sqsClient;
    this.topicArn = topicArn;
  }

  @Override
  public void notify(String streamKey, String eventId) {
    snsClient.publish(
        PublishRequest.builder()
            .topicArn(topicArn)
            .message(streamKey + DELIMITER + eventId)
            .messageAttributes(
                Map.of(
                    STREAM_KEY_ATTRIBUTE,
                    MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(streamKey)
                        .build()))
            .build());
  }

  @Override
  public void subscribe(NotificationHandler handler) {
    handlers.add(handler);
  }

  @Override
  public void start() {
    String queueName = "odyssey-" + UUID.randomUUID();
    queueUrl =
        sqsClient
            .createQueue(
                CreateQueueRequest.builder()
                    .queueName(queueName)
                    .attributes(Map.of(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "300"))
                    .build())
            .queueUrl();

    queueArn =
        sqsClient
            .getQueueAttributes(
                GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build())
            .attributes()
            .get(QueueAttributeName.QUEUE_ARN);

    // Allow SNS to send messages to this SQS queue
    String policy =
        """
        {
          "Version": "2012-10-17",
          "Statement": [
            {
              "Effect": "Allow",
              "Principal": {"Service": "sns.amazonaws.com"},
              "Action": "sqs:SendMessage",
              "Resource": "%s",
              "Condition": {
                "ArnEquals": {
                  "aws:SourceArn": "%s"
                }
              }
            }
          ]
        }
        """
            .formatted(queueArn, topicArn);

    sqsClient.setQueueAttributes(
        SetQueueAttributesRequest.builder()
            .queueUrl(queueUrl)
            .attributes(Map.of(QueueAttributeName.POLICY, policy))
            .build());

    SubscribeResponse subscribeResponse =
        snsClient.subscribe(
            SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .build());
    subscriptionArn = subscribeResponse.subscriptionArn();

    pollerThread = Thread.ofVirtual().name("odyssey-sns-poller").start(this::pollLoop);
    running = true;
  }

  @Override
  public void stop() {
    running = false;
    if (pollerThread != null) {
      pollerThread.interrupt();
      try {
        pollerThread.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      pollerThread = null;
    }
    if (subscriptionArn != null) {
      try {
        snsClient.unsubscribe(
            UnsubscribeRequest.builder().subscriptionArn(subscriptionArn).build());
      } catch (Exception e) {
        log.warn("Failed to unsubscribe from SNS topic: {}", e.getMessage());
      }
      subscriptionArn = null;
    }
    if (queueUrl != null) {
      try {
        sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build());
      } catch (Exception e) {
        log.warn("Failed to delete temporary SQS queue: {}", e.getMessage());
      }
      queueUrl = null;
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void pollLoop() {
    while (running && !Thread.currentThread().isInterrupted()) {
      try {
        ReceiveMessageResponse response =
            sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .waitTimeSeconds(20)
                    .maxNumberOfMessages(10)
                    .build());

        List<Message> messages = response.messages();
        if (messages.isEmpty()) {
          continue;
        }

        List<DeleteMessageBatchRequestEntry> deleteEntries =
            new java.util.ArrayList<>(messages.size());

        for (int i = 0; i < messages.size(); i++) {
          Message message = messages.get(i);
          String body = message.body();
          parseAndDispatch(body);
          deleteEntries.add(
              DeleteMessageBatchRequestEntry.builder()
                  .id(String.valueOf(i))
                  .receiptHandle(message.receiptHandle())
                  .build());
        }

        sqsClient.deleteMessageBatch(
            DeleteMessageBatchRequest.builder().queueUrl(queueUrl).entries(deleteEntries).build());
      } catch (Exception e) {
        if (Thread.currentThread().isInterrupted() || !running) {
          break;
        }
        log.warn("Error polling SQS queue: {}", e.getMessage());
      }
    }
  }

  private void parseAndDispatch(String body) {
    // SNS wraps the message in a JSON envelope when delivering to SQS.
    // Extract the original message from the "Message" field.
    String payload = extractSnsMessage(body);
    int delimiterIndex = payload.indexOf(DELIMITER);
    if (delimiterIndex < 0) {
      return;
    }
    String streamKey = payload.substring(0, delimiterIndex);
    String eventId = payload.substring(delimiterIndex + 1);
    for (NotificationHandler handler : handlers) {
      handler.onNotification(streamKey, eventId);
    }
  }

  private String extractSnsMessage(String body) {
    // SNS delivers to SQS in a JSON envelope: {"Type":"Notification","Message":"...","..."}
    // We extract the "Message" field value. Using simple string parsing to avoid a JSON dependency.
    String marker = "\"Message\"";
    int keyIndex = body.indexOf(marker);
    if (keyIndex < 0) {
      // Not wrapped in SNS envelope — return raw body (e.g., in tests or direct SQS publish)
      return body;
    }
    int colonIndex = body.indexOf(':', keyIndex + marker.length());
    if (colonIndex < 0) {
      return body;
    }
    int openQuote = body.indexOf('"', colonIndex + 1);
    if (openQuote < 0) {
      return body;
    }
    int closeQuote = findClosingQuote(body, openQuote + 1);
    if (closeQuote < 0) {
      return body;
    }
    return unescape(body.substring(openQuote + 1, closeQuote));
  }

  private int findClosingQuote(String s, int from) {
    for (int i = from; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\') {
        i++; // skip escaped character
      } else if (c == '"') {
        return i;
      }
    }
    return -1;
  }

  private String unescape(String s) {
    if (s.indexOf('\\') < 0) {
      return s;
    }
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        i++;
        sb.append(s.charAt(i));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
