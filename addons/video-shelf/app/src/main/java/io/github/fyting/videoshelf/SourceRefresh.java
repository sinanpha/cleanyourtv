package io.github.fyting.videoshelf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;

/**
 * Asks the source apps to write their rows again now (options.xml source_refresh). SmartTube rewrites its home screen
 * channels only every 15 minutes, the shortest period WorkManager allows, so a video watched a minute ago reached the
 * History row up to 15 minutes later. Its receiver for the boot broadcasts rewrites them at once instead. At most once
 * per source_refresh_gap_seconds, since each run fetches three feeds and rewrites about a hundred rows.
 */
final class SourceRefresh {

    /** How long after asking to read the rows again: SmartTube rewrote all three in 5 to 7 s on a MiTV-MOOR2. */
    static final long REREAD_MS = 12_000;
    /** A boot broadcast that any app may send; SmartTube's UpdateChannelsReceiver answers it like the others it lists. */
    static final String ACTION = "android.intent.action.QUICKBOOT_POWERON";
    static final long NEVER = -1;

    private static long askedAt = NEVER;

    private SourceRefresh() {
    }

    /**
     * Asks every installed source_refresh receiver, unless that was done less than the gap ago. Returns how long to wait
     * before reading the rows again, or 0 when nothing was asked.
     */
    static synchronized long ask(Context context) {
        String[] receivers = context.getResources().getStringArray(R.array.source_refresh);
        long gap = context.getResources().getInteger(R.integer.source_refresh_gap_seconds) * 1000L;
        long now = SystemClock.elapsedRealtime();
        if (receivers.length == 0 || !due(askedAt, now, gap)) return 0;
        boolean asked = false;
        for (String receiver : receivers) {
            ComponentName target = ComponentName.unflattenFromString(receiver.trim());
            if (target == null || !enabled(context, target.getPackageName())) continue;
            context.sendBroadcast(new Intent(ACTION).setComponent(target));
            asked = true;
        }
        if (!asked) return 0;
        askedAt = now;
        return REREAD_MS;
    }

    static boolean due(long askedAt, long now, long gap) {
        return askedAt == NEVER || now - askedAt >= gap;
    }

    private static boolean enabled(Context context, String pkg) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
            return info.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
