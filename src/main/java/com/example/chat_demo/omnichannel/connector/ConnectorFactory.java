package com.example.chat_demo.omnichannel.connector;

import com.example.chat_demo.common.ChannelType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ConnectorFactory - Factory để lấy connector tương ứng với platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorFactory {
    
    private final List<PlatformConnector> connectors;
    private Map<ChannelType, PlatformConnector> connectorMap;
    
    @PostConstruct
    public void init() {
        connectorMap = connectors.stream()
            .collect(Collectors.toMap(
                PlatformConnector::getChannelType,
                Function.identity()
            ));
        log.info("Initialized connectors for platforms: {}", connectorMap.keySet());
    }
    
    public PlatformConnector getConnector(ChannelType channelType) {
        PlatformConnector connector = connectorMap.get(channelType);
        if (connector == null) {
            throw new IllegalArgumentException(
                "No connector found for channel: " + channelType
            );
        }
        return connector;
    }
}

