# Global Telegram Build Notifier

`global-telegram-build-notifier` is a Jenkins controller plugin that sends global Telegram build notifications through a Jenkins `RunListener`.

It is intentionally independent from Jenkinsfile shared-library notification code:

```text
mailhandler
  -> project, environment, customer, deployment, and business notifications

Global Telegram Build Notifier
  -> Jenkins controller level build started/completed operations notifications
```

## Supported Jobs

- Freestyle jobs
- Declarative Pipeline
- Scripted Pipeline
- Multibranch Pipeline branch builds
- Jobs using developer-maintained Jenkinsfiles
- Jobs that do not use the shared library
- Manual, Timer, SCM, Upstream, Remote, and other Jenkins causes

Because the plugin listens to `Run<?, ?>`, agent-side installation is not required.

## Version Choices

- Jenkins Plugin Parent: `org.jenkins-ci.plugins:plugin:6.2211.v27f680c93c53`
- Jenkins Core baseline: `2.479.3`
- Java: 17 runtime, Java 17 source level from plugin parent
- Maven used for validation here: `3.9.11`
- Credentials Plugin: managed through Jenkins plugin BOM for `2.479.x`
- Plain Credentials Plugin: managed through Jenkins plugin BOM for `2.479.x`
- Test Harness/JUnit: managed by Jenkins plugin parent
- Pipeline test dependencies:
  - `workflow-job:1500.v29502eb_5182e`
  - `workflow-cps:4009.v0089238351a_9`
  - `workflow-basic-steps:1058.vcb_fc1e3a_21a_9`

API note: this baseline uses `org.kohsuke.stapler.StaplerRequest` in `GlobalConfiguration.configure`. Newer Jenkins baselines may use `StaplerRequest2`.

## Security Design

- Telegram Bot Token is never stored in Java, Jelly, README examples, tests, or Git.
- Token must be a Jenkins Secret Text credential.
- Global configuration stores only the credential ID.
- Credential dropdown lists only `StringCredentials`.
- Logs never include the Telegram API URL, because the URL contains the token.
- HTTP errors log only safe status messages.
- Notification failure never changes the original build result.
- `onStarted`, `onCompleted`, and `onFinalized` do not block on Telegram HTTP calls.
- `onCompleted` is the only terminal notification event. `onFinalized` intentionally does not send.

## Queue Design

Notifications are submitted to an in-memory bounded `ThreadPoolExecutor`:

- core pool size: 1
- max pool size: 2
- queue capacity: 500
- daemon thread name prefix: `global-telegram-build-notifier-`
- rejected notifications are dropped and counted

The queue is in memory. If the Jenkins controller restarts, queued but unsent notifications may be lost. A future durable design could write a small spool record to `$JENKINS_HOME/global-telegram-build-notifier/spool/` and remove each record only after Telegram returns 2xx.

## Telegram 429

Telegram HTTP 429 responses are parsed for `parameters.retry_after`.

- bounded retry: max one retry
- retry runs in the worker thread, never in the RunListener callback
- retry is skipped if `retry_after` exceeds `maxRetryAfterSeconds`
- no unbounded retry loop

## Proxy

The HTTP client uses `hudson.ProxyConfiguration.open(URL)`, so Jenkins global proxy configuration is reused. Users do not need to enter proxy credentials again in this plugin.

## Global Configuration

Manage Jenkins -> System -> Global Telegram Build Notification

Fields:

- Enabled
- Jenkins display name
- Telegram Bot Token Credential
- Telegram Chat ID
- Telegram Message Thread ID
- Notify on started
- Notify on success
- Notify on failure
- Notify on unstable
- Notify on aborted
- Include Job name regex
- Exclude Job name regex
- Telegram API URL
- Connect timeout milliseconds
- Read timeout milliseconds
- Maximum 429 retry delay seconds

Recommended production defaults:

- started: off
- success: off
- failure: on
- unstable: on
- aborted: on

## Credentials

Create the Telegram bot token as a Jenkins credential:

- Kind: Secret text
- Scope: System or Global, depending on your Jenkins credential policy
- ID: `telegram-bot-token`
- Secret: the Telegram bot token

Credential rotation is supported. Each send resolves the current Secret Text by credential ID, so updating the same credential ID takes effect on the next notification without rebuilding the plugin.

## Telegram Setup

Bot creation:

1. Talk to BotFather.
2. Create a bot.
3. Store the token as Jenkins Secret Text.

Group chat ID:

1. Add the bot to the group.
2. Send a message in the group.
3. Call Telegram `getUpdates` outside Jenkins or use an internal admin tool.
4. Use the numeric `chat.id`, often like `-1001234567890`.

Topic message thread ID:

1. Use a Telegram forum topic group.
2. Inspect a topic message via `getUpdates`.
3. Use `message_thread_id`.
4. Leave empty for normal groups.

## JCasC

See [docs/jcasc.yaml](docs/jcasc.yaml).

The root key is:

```yaml
unclassified:
  globalTelegramBuildNotifier:
```

Verify JCasC load from Manage Jenkins -> Configuration as Code -> View Configuration, or by checking the Global Configuration UI after reload.

## Build

```bash
mvn clean verify
mvn hpi:run
```

The installable file is generated at:

```text
target/global-telegram-build-notifier.hpi
```

## Install

Test Jenkins:

1. Build the plugin with `mvn clean verify`.
2. Upload `target/global-telegram-build-notifier.hpi` in Manage Jenkins -> Plugins -> Advanced settings.
3. Restart Jenkins if required by your plugin policy.
4. Create the Secret Text credential.
5. Configure Global Telegram Build Notification.
6. Run a Freestyle and Pipeline job.

Dynamic load may work on some Jenkins versions, but do not assume every controller can load or upgrade this plugin without restart.

Agents do not need the plugin.

## Upgrade, Disable, Rollback

- Upgrade: back up `$JENKINS_HOME`, install the new `.hpi`, restart if required.
- Disable: Jenkins stops invoking this plugin after disable/restart. The executor is shut down by plugin lifecycle termination.
- Rollback: keep the previous `.hpi` or `.jpi`, uninstall or downgrade the plugin, then restart Jenkins.
- Backup: include `$JENKINS_HOME/plugins/global-telegram-build-notifier.*` and global config XML.

## Avoiding Duplicate mailhandler Notifications

POC options:

- Use a separate Telegram operations group.
- Use include/exclude regex to skip jobs already covered by Jenkinsfile `mailhandler`.
- For production, start with only FAILURE, UNSTABLE, and ABORTED.

Future improvement:

- Job-level opt-out property.
- Folder-level policy.
- Persistent notification audit action.

## Tests

Current tests cover:

- Freestyle listener path
- Pipeline SUCCESS
- Pipeline FAILURE
- Pipeline UNSTABLE
- Pipeline ABORTED
- started notification
- completed notification dedupe
- `onFinalized` no duplicate send
- disabled plugin
- result filters
- include regex
- exclude regex
- cause resolver unit cases
- null or missing root URL through DTO behavior
- local mock HTTP server for Telegram 200
- Telegram 429 retry_after parsing
- Telegram 500 non-JSON response
- message thread ID form body
- token not present in request body
- Unicode-safe Telegram truncation

Tests never call the real Telegram API.

Some Jenkins trigger causes are easier to test as resolver unit tests than full scheduled Jenkins triggers, because JenkinsRule setup for real SCM and remote triggers would add unrelated infrastructure.

## Checklist

POC:

- Run `mvn clean verify`
- Run `mvn hpi:run`
- Create Secret Text Credential
- Configure test Telegram group
- Test Freestyle
- Test Pipeline
- Test SUCCESS, FAILURE, UNSTABLE, ABORTED
- Verify queue and timeout behavior
- Verify notification failure does not affect build result

Test environment:

- Back up `$JENKINS_HOME`
- Install plugin
- Restart Jenkins
- Verify Global Configuration
- Verify JCasC
- Verify Multibranch
- Verify SCM, Timer, Upstream
- Verify proxy
- Verify 401, 403, 429, 500
- Verify Credential rotation
- Verify include/exclude
- Verify mailhandler de-duplication policy

Production:

- Back up plugin and `$JENKINS_HOME`
- Keep previous `.hpi` or `.jpi`
- Enable only FAILURE, UNSTABLE, ABORTED first
- Configure include/exclude
- Use Telegram operations group
- Monitor plugin log
- Monitor dropped queue count
- Record rollback steps
- Create Bot Token rotation SOP
- Add Jenkins Core upgrade compatibility testing
