package com.example.chat_demo.webhook.controller;

import com.example.chat_demo.omnichannel.service.DiscordGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DiscordTestController - Các API hỗ trợ test nhanh Discord Bot
 */
@Slf4j
@RestController
@Tag(name = "Discord Test API", description = "Các endpoint hỗ trợ kiểm tra nhanh Discord bot/token.")
@RequestMapping("/test/discord")
@RequiredArgsConstructor
public class DiscordTestController {

    @Value("${platform.discord.bot-token:}")
    private String botToken;

    @Value("${platform.discord.default-channel-id:}")
    private String defaultChannelId;

    private final RestTemplate restTemplate;
    private final DiscordGatewayService discordGatewayService;

    /**
     * Gửi message test tới channel (mặc định hoặc truyền channelId)
     */
    @Operation(summary = "Gửi message test", description = "Gửi thử một tin nhắn text tới Discord channel.")
    @PostMapping("/send-message")
    public ResponseEntity<Map<String, Object>> sendTestMessage(
            @RequestParam(required = false) String channelId,
            @RequestParam String text) {
        try {
            String targetChannel = channelId != null && !channelId.isBlank() ? channelId : defaultChannelId;
            if (targetChannel == null || targetChannel.isBlank()) {
                throw new IllegalArgumentException("channelId is required (either param or platform.discord.default-channel-id)");
            }

            String url = "https://discord.com/api/v10/channels/" + targetChannel + "/messages";
            log.info("[API] POST /test/discord/send-message channelId={} text={}", targetChannel, text);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("content", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bot " + botToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            log.info("[API] POST /test/discord/send-message success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending Discord test message", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Lấy thông tin channel (name, guild, v.v.)
     */
    @Operation(summary = "Xem channel info", description = "Lấy thông tin một Discord channel dựa trên channelId.")
    @GetMapping("/channel-info")
    public ResponseEntity<Map<String, Object>> getChannelInfo(@RequestParam String channelId) {
        try {
            String url = "https://discord.com/api/v10/channels/" + channelId;
            log.info("[API] GET /test/discord/channel-info channelId={}", channelId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bot " + botToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            Map<String, Object> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, request, Map.class).getBody();

            log.info("[API] GET /test/discord/channel-info success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting Discord channel info", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Tạo invite link cho channel (text hoặc voice)
     */
    @Operation(summary = "Tạo invite link", description = "Tạo invite để gửi cho user vào channel hoặc voice channel.")
    @PostMapping("/create-invite")
    public ResponseEntity<Map<String, Object>> createInvite(
            @RequestParam(required = false) String channelId,
            @RequestParam(defaultValue = "86400") int maxAge,     // 24h
            @RequestParam(defaultValue = "0") int maxUses,        // 0 = unlimited
            @RequestParam(defaultValue = "false") boolean temporary
    ) {
        try {
            String targetChannel = channelId != null && !channelId.isBlank() ? channelId : defaultChannelId;
            if (targetChannel == null || targetChannel.isBlank()) {
                throw new IllegalArgumentException("channelId is required (either param or platform.discord.default-channel-id)");
            }

            String url = "https://discord.com/api/v10/channels/" + targetChannel + "/invites";
            log.info("[API] POST /test/discord/create-invite channelId={} maxAge={} maxUses={} temporary={}",
                    targetChannel, maxAge, maxUses, temporary);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("max_age", maxAge);
            requestBody.put("max_uses", maxUses);
            requestBody.put("temporary", temporary);
            requestBody.put("unique", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bot " + botToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response == null) {
                throw new IllegalStateException("Discord response empty");
            }

            Object code = response.get("code");
            if (code != null) {
                response.put("inviteUrl", "https://discord.gg/" + code);
            }
            response.put("channelId", targetChannel);

            log.info("[API] POST /test/discord/create-invite success code={}", code);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating Discord invite", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Liệt kê các channel mà bot nhìn thấy (TEXT/VOICE)
     */
    @Operation(summary = "Danh sách channel bot join", description = "Trả về danh sách channel (text/voice) mà bot có quyền truy cập.")
    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> listChannels(
            @RequestParam(defaultValue = "ALL") String type
    ) {
        try {
            JDA jda = discordGatewayService.getJda();
            if (jda == null || !discordGatewayService.isReady()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Discord gateway is not ready");
                return ResponseEntity.status(503).body(error);
            }

            String filter = type == null ? "ALL" : type.trim().toUpperCase();
            boolean includeText = filter.equals("ALL") || filter.equals("TEXT");
            boolean includeVoice = filter.equals("ALL") || filter.equals("VOICE");

            List<Map<String, Object>> channels = new ArrayList<>();
            jda.getGuilds().forEach(guild -> {
                if (includeText) {
                    for (TextChannel textChannel : guild.getTextChannels()) {
                        channels.add(buildChannelInfo(textChannel));
                    }
                }
                if (includeVoice) {
                    for (VoiceChannel voiceChannel : guild.getVoiceChannels()) {
                        channels.add(buildChannelInfo(voiceChannel));
                    }
                }
            });

            Map<String, Object> response = new HashMap<>();
            response.put("total", channels.size());
            response.put("channels", channels);
            response.put("filter", filter);

            log.info("[API] GET /test/discord/channels filter={} size={}", filter, channels.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error listing Discord channels", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    private Map<String, Object> buildChannelInfo(GuildChannel channel) {
        Map<String, Object> info = new HashMap<>();
        info.put("channelId", channel.getId());
        info.put("channelName", channel.getName());
        info.put("channelType", channel.getType().name());
        info.put("guildId", channel.getGuild().getId());
        info.put("guildName", channel.getGuild().getName());
        return info;
    }
}



