package io.jenkins.plugins.globaltelegrambuildnotifier;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.init.Terminator;
import hudson.security.ACL;
import hudson.util.Secret;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

@Extension
public class TelegramNotificationService {
    private static final Logger LOGGER = Logger.getLogger(TelegramNotificationService.class.getName());
    private static final int QUEUE_CAPACITY = 500;
    private final TelegramClient client;
    private final TelegramMessageFormatter formatter;
    private final AtomicLong droppedNotificationCount = new AtomicLong();
    private volatile ThreadPoolExecutor executor;

    public TelegramNotificationService() {
        this(new TelegramClient(), new TelegramMessageFormatter());
    }

    TelegramNotificationService(TelegramClient client, TelegramMessageFormatter formatter) {
        this.client = client;
        this.formatter = formatter;
        ensureStarted();
    }

    static TelegramNotificationService get() {
        return Jenkins.get().getExtensionList(TelegramNotificationService.class).get(0);
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void start() {
        get().ensureStarted();
    }

    @Terminator
    public static void stop() {
        if (Jenkins.getInstanceOrNull() != null) {
            for (TelegramNotificationService service : Jenkins.get().getExtensionList(TelegramNotificationService.class)) {
                service.shutdown();
            }
        }
    }

    void enqueue(BuildNotification notification) {
        ThreadPoolExecutor currentExecutor = ensureStarted();
        try {
            currentExecutor.execute(() -> sendSafely(notification));
        } catch (RuntimeException e) {
            droppedNotificationCount.incrementAndGet();
            LOGGER.log(Level.WARNING, "Telegram notification queue is full; notification dropped.");
        }
    }

    long getDroppedNotificationCount() {
        return droppedNotificationCount.get();
    }

    ThreadPoolExecutor ensureStarted() {
        ThreadPoolExecutor current = executor;
        if (current != null && !current.isShutdown()) {
            return current;
        }
        synchronized (this) {
            if (executor == null || executor.isShutdown()) {
                executor = new ThreadPoolExecutor(
                        1,
                        2,
                        60L,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                        new TelegramThreadFactory(),
                        new ThreadPoolExecutor.AbortPolicy());
            }
            return executor;
        }
    }

    void shutdown() {
        ThreadPoolExecutor current = executor;
        if (current != null) {
            current.shutdownNow();
        }
    }

    private void sendSafely(BuildNotification notification) {
        GlobalTelegramBuildNotifierConfiguration config = GlobalTelegramBuildNotifierConfiguration.get();
        Secret token = lookupToken(config.getTokenCredentialId());
        if (token == null) {
            LOGGER.log(Level.WARNING, "Telegram Secret Text credential is missing or invalid.");
            return;
        }
        String chatId = Texts.trimToNull(config.getChatId());
        if (chatId == null) {
            LOGGER.log(Level.WARNING, "Telegram chat ID is not configured.");
            return;
        }
        TelegramRequest request = new TelegramRequest(chatId, config.getMessageThreadId(), formatter.format(notification));
        sendWithBoundedRetry(request, token.getPlainText(), config);
    }

    private Secret lookupToken(String credentialsId) {
        if (Texts.trimToNull(credentialsId) == null) {
            return null;
        }
        StringCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                        StringCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM2,
                        Collections.emptyList()),
                CredentialsMatchers.withId(credentialsId));
        return credentials == null ? null : credentials.getSecret();
    }

    private void sendWithBoundedRetry(TelegramRequest request, String token, GlobalTelegramBuildNotifierConfiguration config) {
        try {
            TelegramResponse response = client.send(request, token, config);
            if (response.isSuccessful()) {
                return;
            }
            if (response.getStatusCode() == 429 && shouldRetry(response, config)) {
                sleepQuietly(response.getRetryAfterSeconds());
                TelegramResponse retryResponse = client.send(request, token, config);
                logNonSuccess(retryResponse.getStatusCode());
                return;
            }
            logNonSuccess(response.getStatusCode());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Telegram notification failed without changing the Jenkins build result: " + e.getClass().getSimpleName());
        }
    }

    private boolean shouldRetry(TelegramResponse response, GlobalTelegramBuildNotifierConfiguration config) {
        Integer retryAfterSeconds = response.getRetryAfterSeconds();
        return retryAfterSeconds != null && retryAfterSeconds <= config.getMaxRetryAfterSeconds();
    }

    private void sleepQuietly(Integer retryAfterSeconds) {
        try {
            TimeUnit.SECONDS.sleep(retryAfterSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logNonSuccess(int statusCode) {
        if (statusCode == 401) {
            LOGGER.log(Level.WARNING, "Telegram notification rejected with 401; check bot token credential.");
        } else if (statusCode == 403) {
            LOGGER.log(Level.WARNING, "Telegram notification rejected with 403; check bot access to the chat.");
        } else if (statusCode == 429) {
            LOGGER.log(Level.WARNING, "Telegram notification rate limited with 429; notification dropped.");
        } else if (statusCode >= 500) {
            LOGGER.log(Level.WARNING, "Telegram server error " + statusCode + "; notification dropped.");
        } else {
            LOGGER.log(Level.WARNING, "Telegram notification failed with HTTP " + statusCode + ".");
        }
    }

    private static final class TelegramThreadFactory implements ThreadFactory {
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "global-telegram-build-notifier-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
