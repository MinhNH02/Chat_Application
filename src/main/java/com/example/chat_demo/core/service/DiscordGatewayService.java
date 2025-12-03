package com.example.chat_demo.core.service;

import com.example.chat_demo.common.ChannelType;
import com.example.chat_demo.core.model.UnifiedMessage;
import com.example.chat_demo.core.router.OmnichannelRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * DiscordGatewayService - Lắng nghe tin nhắn từ Discord thông qua JDA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordGatewayService extends ListenerAdapter {

    private final OmnichannelRouter router;

    @Value("${platform.discord.bot-token:}")
    private String botToken;

    private JDA jda;

    @PostConstruct
    public void start() {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Discord bot token is not configured. Discord gateway will not start.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.DIRECT_MESSAGES
                    )
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .addEventListeners(this)
                    .build();

            log.info("Discord gateway started successfully");
        } catch (Exception e) {
            log.error("Failed to start Discord gateway", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (jda != null) {
            log.info("Shutting down Discord gateway");
            jda.shutdownNow();
        }
    }

    /**
     * Trả về instance JDA hiện tại để các API khác sử dụng.
     */
    public JDA getJda() {
        return jda;
    }

    /**
     * Kiểm tra trạng thái kết nối của gateway.
     */
    public boolean isReady() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        MessageChannel channel = event.getChannel();
        var message = event.getMessage();

        try {
            UnifiedMessage unifiedMessage = UnifiedMessage.builder()
                    .channelType(ChannelType.DISCORD)
                    .platformUserId(event.getAuthor().getId()) // User ID để lưu vào DB
                    .platformMessageId(message.getId())
                    .content(message.getContentDisplay())
                    .messageType("text")
                    .timestamp(LocalDateTime.ofInstant(
                            message.getTimeCreated().toInstant(),
                            ZoneId.systemDefault()))
                    .username(event.getAuthor().getName())
                    .firstName(event.getAuthor().getGlobalName())
                    .channelId(channel.getId()) // Channel ID để reply
                    .rawData(message)
                    .build();

            router.routeMessage(unifiedMessage);
            log.info("Received Discord message {} from channel {}", message.getId(), channel.getId());
        } catch (Exception e) {
            log.error("Error routing Discord message", e);
        }
    }
}

