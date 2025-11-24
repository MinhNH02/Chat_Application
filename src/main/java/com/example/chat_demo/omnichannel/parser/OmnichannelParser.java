package com.example.chat_demo.omnichannel.parser;

import com.example.chat_demo.common.ChannelType;
import com.example.chat_demo.omnichannel.model.UnifiedMessage;
import com.example.chat_demo.omnichannel.parser.platform.MessengerParser;
import com.example.chat_demo.omnichannel.parser.platform.TelegramParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OmnichannelParser - Chuẩn hóa message từ các platform về UnifiedMessage
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OmnichannelParser {
    
    private final TelegramParser telegramParser;
    private final MessengerParser messengerParser;
    
    /**
     * Parse raw webhook data từ platform thành UnifiedMessage
     * @param channelType Loại platform
     * @param rawData Dữ liệu thô từ webhook
     * @return UnifiedMessage đã được chuẩn hóa
     */
    public UnifiedMessage parse(ChannelType channelType, Object rawData) {
        log.debug("Parsing message from {} platform", channelType);
        
        return switch (channelType) {
            case TELEGRAM -> telegramParser.parse(rawData);
            case MESSENGER -> messengerParser.parse(rawData);
        };
    }
}

