package com.Impostor.Bot; // Asegúrate de que este package sea el correcto

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class BotInitializer {

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBot telegramBot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        try {
            api.registerBot(telegramBot);
            System.out.println("✅ Bot registrado y conectado exitosamente a Telegram");
        } catch (TelegramApiException e) {
            System.err.println("❌ Error al registrar el bot: " + e.getMessage());
            e.printStackTrace();
        }
        return api;
    }
}