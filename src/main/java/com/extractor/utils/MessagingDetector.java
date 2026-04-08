package com.extractor.utils;

import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

import java.util.*;

public class MessagingDetector {

    private static final Map<String, String> MESSAGING_PATTERNS = new HashMap<>();
    
    static {
        MESSAGING_PATTERNS.put("javax.jms", "jms");
        MESSAGING_PATTERNS.put("jakarta.jms", "jms");
        MESSAGING_PATTERNS.put("org.apache.kafka", "kafka");
        MESSAGING_PATTERNS.put("org.springframework.kafka", "kafka");
        MESSAGING_PATTERNS.put("org.springframework.amqp", "rabbitmq");
        MESSAGING_PATTERNS.put("com.rabbitmq", "rabbitmq");
        MESSAGING_PATTERNS.put("org.apache.activemq", "activemq");
        MESSAGING_PATTERNS.put("org.springframework.jms", "spring-jms");
    }
    
    private static final Set<String> PUBLISHER_INDICATORS = new HashSet<>(Arrays.asList(
        "MessageProducer", "QueueSender", "TopicPublisher",
        "KafkaTemplate", "KafkaProducer",
        "RabbitTemplate", "AmqpTemplate",
        "JmsTemplate", "JmsMessagingTemplate"
    ));
    
    private static final Set<String> CONSUMER_INDICATORS = new HashSet<>(Arrays.asList(
        "MessageConsumer", "QueueReceiver", "TopicSubscriber",
        "KafkaConsumer",
        "MessageDriven", "JmsListener", "KafkaListener", "RabbitListener"
    ));
    
    private static final Set<String> PUBLISHER_METHOD_PATTERNS = new HashSet<>(Arrays.asList(
        "send", "sendMessage", "publish", "produce", "convertAndSend"
    ));

    public static MessagingInfo detectMessaging(CtType<?> ctType) {
        Set<String> messagingTypes = new HashSet<>();
        boolean isPublisher = false;
        boolean isConsumer = false;
        
        Set<CtTypeReference<?>> allTypes = new HashSet<>();
        allTypes.addAll(ctType.getReferencedTypes());
        
        for (CtField<?> field : ctType.getFields()) {
            if (field.getType() != null) {
                allTypes.add(field.getType());
            }
        }
        
        for (CtMethod<?> method : ctType.getMethods()) {
            if (method.getType() != null) {
                allTypes.add(method.getType());
            }
        }
        
        for (CtTypeReference<?> typeRef : allTypes) {
            String qualifiedName = typeRef.getQualifiedName();
            
            for (Map.Entry<String, String> entry : MESSAGING_PATTERNS.entrySet()) {
                if (qualifiedName.startsWith(entry.getKey())) {
                    messagingTypes.add(entry.getValue());
                    break;
                }
            }
            
            String simpleName = typeRef.getSimpleName();
            if (PUBLISHER_INDICATORS.contains(simpleName)) {
                isPublisher = true;
            }
            if (CONSUMER_INDICATORS.contains(simpleName)) {
                isConsumer = true;
            }
        }
        
        for (String annotation : ctType.getAnnotations().stream()
                .map(a -> a.getAnnotationType().getSimpleName())
                .toArray(String[]::new)) {
            if (CONSUMER_INDICATORS.contains(annotation)) {
                isConsumer = true;
            }
        }
        
        for (CtMethod<?> method : ctType.getMethods()) {
            for (String annotation : method.getAnnotations().stream()
                    .map(a -> a.getAnnotationType().getSimpleName())
                    .toArray(String[]::new)) {
                if (CONSUMER_INDICATORS.contains(annotation)) {
                    isConsumer = true;
                }
            }
            
            String methodName = method.getSimpleName().toLowerCase();
            for (String pattern : PUBLISHER_METHOD_PATTERNS) {
                if (methodName.contains(pattern.toLowerCase()) && !messagingTypes.isEmpty()) {
                    isPublisher = true;
                    break;
                }
            }
        }
        
        if (messagingTypes.isEmpty()) {
            return new MessagingInfo(null, null);
        }
        
        String messagingType = String.join(",", new TreeSet<>(messagingTypes));
        
        String role = null;
        if (isPublisher && isConsumer) {
            role = "both";
        } else if (isPublisher) {
            role = "publisher";
        } else if (isConsumer) {
            role = "consumer";
        }
        
        return new MessagingInfo(messagingType, role);
    }
    
    public static class MessagingInfo {
        private final String messagingType;
        private final String messagingRole;
        
        public MessagingInfo(String messagingType, String messagingRole) {
            this.messagingType = messagingType;
            this.messagingRole = messagingRole;
        }
        
        public String getMessagingType() {
            return messagingType;
        }
        
        public String getMessagingRole() {
            return messagingRole;
        }
    }
}
