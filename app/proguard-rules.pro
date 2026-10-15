# Silence — keep VPN / DNS entry points if minify is enabled later.
-keep class com.silence.blocker.vpn.SilenceVpnService { *; }
-keep class com.silence.blocker.vpn.BootCompletedReceiver { *; }
-keep class com.silence.blocker.media.SilenceNotificationListener { *; }
-keep class com.silence.blocker.lists.ListUpdateWorker { *; }
-keep class com.silence.blocker.SilenceApp { *; }
