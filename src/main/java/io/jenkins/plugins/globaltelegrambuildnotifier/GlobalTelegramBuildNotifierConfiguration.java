package io.jenkins.plugins.globaltelegrambuildnotifier;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor.FormException;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
@Symbol("globalTelegramBuildNotifier")
public class GlobalTelegramBuildNotifierConfiguration extends GlobalConfiguration {
    static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;
    static final int DEFAULT_MAX_RETRY_AFTER_SECONDS = 30;
    static final String DEFAULT_TELEGRAM_API_URL = "https://api.telegram.org";

    private boolean enabled;
    private String jenkinsDisplayName;
    private String tokenCredentialId;
    private String chatId;
    private String messageThreadId;
    private boolean notifyOnStarted;
    private boolean notifyOnSuccess;
    private boolean notifyOnFailure = true;
    private boolean notifyOnUnstable = true;
    private boolean notifyOnAborted = true;
    private String includeJobRegex;
    private String excludeJobRegex;
    private String telegramApiUrl = DEFAULT_TELEGRAM_API_URL;
    private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
    private int readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;
    private int maxRetryAfterSeconds = DEFAULT_MAX_RETRY_AFTER_SECONDS;
    private transient volatile Pattern includeJobPattern;
    private transient volatile Pattern excludeJobPattern;

    public GlobalTelegramBuildNotifierConfiguration() {
        load();
        compilePatterns();
    }

    public static GlobalTelegramBuildNotifierConfiguration get() {
        return GlobalConfiguration.all().get(GlobalTelegramBuildNotifierConfiguration.class);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJenkinsDisplayName() {
        return jenkinsDisplayName;
    }

    public void setJenkinsDisplayName(String jenkinsDisplayName) {
        this.jenkinsDisplayName = Texts.trimToNull(jenkinsDisplayName);
    }

    String effectiveJenkinsDisplayName() {
        String configured = Texts.trimToNull(jenkinsDisplayName);
        return configured == null ? "Jenkins" : configured;
    }

    public String getTokenCredentialId() {
        return tokenCredentialId;
    }

    public void setTokenCredentialId(String tokenCredentialId) {
        this.tokenCredentialId = Texts.trimToNull(tokenCredentialId);
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = Texts.trimToNull(chatId);
    }

    public String getMessageThreadId() {
        return messageThreadId;
    }

    public void setMessageThreadId(String messageThreadId) {
        this.messageThreadId = Texts.trimToNull(messageThreadId);
    }

    public boolean isNotifyOnStarted() {
        return notifyOnStarted;
    }

    public void setNotifyOnStarted(boolean notifyOnStarted) {
        this.notifyOnStarted = notifyOnStarted;
    }

    public boolean isNotifyOnSuccess() {
        return notifyOnSuccess;
    }

    public void setNotifyOnSuccess(boolean notifyOnSuccess) {
        this.notifyOnSuccess = notifyOnSuccess;
    }

    public boolean isNotifyOnFailure() {
        return notifyOnFailure;
    }

    public void setNotifyOnFailure(boolean notifyOnFailure) {
        this.notifyOnFailure = notifyOnFailure;
    }

    public boolean isNotifyOnUnstable() {
        return notifyOnUnstable;
    }

    public void setNotifyOnUnstable(boolean notifyOnUnstable) {
        this.notifyOnUnstable = notifyOnUnstable;
    }

    public boolean isNotifyOnAborted() {
        return notifyOnAborted;
    }

    public void setNotifyOnAborted(boolean notifyOnAborted) {
        this.notifyOnAborted = notifyOnAborted;
    }

    public String getIncludeJobRegex() {
        return includeJobRegex;
    }

    public void setIncludeJobRegex(String includeJobRegex) {
        this.includeJobRegex = Texts.trimToNull(includeJobRegex);
        this.includeJobPattern = compileOrNull(this.includeJobRegex);
    }

    public String getExcludeJobRegex() {
        return excludeJobRegex;
    }

    public void setExcludeJobRegex(String excludeJobRegex) {
        this.excludeJobRegex = Texts.trimToNull(excludeJobRegex);
        this.excludeJobPattern = compileOrNull(this.excludeJobRegex);
    }

    public String getTelegramApiUrl() {
        return telegramApiUrl;
    }

    public void setTelegramApiUrl(String telegramApiUrl) {
        this.telegramApiUrl = Texts.trimToNull(telegramApiUrl);
        if (this.telegramApiUrl == null) {
            this.telegramApiUrl = DEFAULT_TELEGRAM_API_URL;
        }
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis > 0 ? connectTimeoutMillis : DEFAULT_CONNECT_TIMEOUT_MILLIS;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis > 0 ? readTimeoutMillis : DEFAULT_READ_TIMEOUT_MILLIS;
    }

    public int getMaxRetryAfterSeconds() {
        return maxRetryAfterSeconds;
    }

    public void setMaxRetryAfterSeconds(int maxRetryAfterSeconds) {
        this.maxRetryAfterSeconds = maxRetryAfterSeconds >= 0 ? maxRetryAfterSeconds : DEFAULT_MAX_RETRY_AFTER_SECONDS;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        req.bindJSON(this, json);
        compilePatterns();
        save();
        return true;
    }

    boolean shouldNotifyStarted(String jobFullName) {
        return enabled && notifyOnStarted && matchesJob(jobFullName);
    }

    boolean shouldNotifyCompleted(String jobFullName, Result result) {
        if (!enabled || !matchesJob(jobFullName) || result == null) {
            return false;
        }
        if (Result.SUCCESS.equals(result)) {
            return notifyOnSuccess;
        }
        if (Result.FAILURE.equals(result)) {
            return notifyOnFailure;
        }
        if (Result.UNSTABLE.equals(result)) {
            return notifyOnUnstable;
        }
        if (Result.ABORTED.equals(result)) {
            return notifyOnAborted;
        }
        return false;
    }

    boolean matchesJob(String jobFullName) {
        Pattern include = getIncludeJobPattern();
        Pattern exclude = getExcludeJobPattern();
        if (exclude != null && exclude.matcher(jobFullName).find()) {
            return false;
        }
        return include == null || include.matcher(jobFullName).find();
    }

    private Pattern getIncludeJobPattern() {
        if (includeJobPattern == null && includeJobRegex != null) {
            includeJobPattern = compileOrNull(includeJobRegex);
        }
        return includeJobPattern;
    }

    private Pattern getExcludeJobPattern() {
        if (excludeJobPattern == null && excludeJobRegex != null) {
            excludeJobPattern = compileOrNull(excludeJobRegex);
        }
        return excludeJobPattern;
    }

    private void compilePatterns() {
        includeJobPattern = compileOrNull(includeJobRegex);
        excludeJobPattern = compileOrNull(excludeJobRegex);
    }

    private static Pattern compileOrNull(String regex) {
        String trimmed = Texts.trimToNull(regex);
        if (trimmed == null) {
            return null;
        }
        try {
            return Pattern.compile(trimmed);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    @RequirePOST
    public ListBoxModel doFillTokenCredentialIdItems(@QueryParameter String tokenCredentialId) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        ItemGroup<?> root = Jenkins.get();
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM2, root, StringCredentials.class, Collections.emptyList())
                .includeCurrentValue(tokenCredentialId);
    }

    public FormValidation doCheckChatId(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        String trimmed = Texts.trimToNull(value);
        if (trimmed == null) {
            return FormValidation.warning("No chat ID configured. Notifications will be skipped.");
        }
        if (!trimmed.matches("^-?\\d+$|^@[-_A-Za-z0-9]+$")) {
            return FormValidation.error("Use a numeric chat ID or @channelusername.");
        }
        return FormValidation.ok();
    }

    public FormValidation doCheckMessageThreadId(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        String trimmed = Texts.trimToNull(value);
        if (trimmed == null || trimmed.matches("^\\d+$")) {
            return FormValidation.ok();
        }
        return FormValidation.error("Message thread ID must be a positive integer.");
    }

    public FormValidation doCheckIncludeJobRegex(@QueryParameter String value) {
        return checkRegex(value);
    }

    public FormValidation doCheckExcludeJobRegex(@QueryParameter String value) {
        return checkRegex(value);
    }

    private FormValidation checkRegex(String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        String trimmed = Texts.trimToNull(value);
        if (trimmed == null) {
            return FormValidation.ok();
        }
        try {
            Pattern.compile(trimmed);
            return FormValidation.ok();
        } catch (PatternSyntaxException e) {
            return FormValidation.error("Invalid regular expression: " + e.getDescription());
        }
    }

    public FormValidation doCheckTelegramApiUrl(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        String trimmed = Texts.trimToNull(value);
        if (trimmed == null || trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return FormValidation.ok();
        }
        return FormValidation.error("Use an absolute HTTP or HTTPS URL.");
    }

    public FormValidation doCheckConnectTimeoutMillis(@QueryParameter String value) {
        return checkPositiveInteger(value);
    }

    public FormValidation doCheckReadTimeoutMillis(@QueryParameter String value) {
        return checkPositiveInteger(value);
    }

    public FormValidation doCheckMaxRetryAfterSeconds(@QueryParameter String value) {
        return checkNonNegativeInteger(value);
    }

    private FormValidation checkPositiveInteger(String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        try {
            if (Integer.parseInt(value) > 0) {
                return FormValidation.ok();
            }
        } catch (NumberFormatException ignored) {
            return FormValidation.error("Use a positive integer.");
        }
        return FormValidation.error("Use a positive integer.");
    }

    private FormValidation checkNonNegativeInteger(String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        try {
            if (Integer.parseInt(value) >= 0) {
                return FormValidation.ok();
            }
        } catch (NumberFormatException ignored) {
            return FormValidation.error("Use a non-negative integer.");
        }
        return FormValidation.error("Use a non-negative integer.");
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Global Telegram Build Notification";
    }
}
