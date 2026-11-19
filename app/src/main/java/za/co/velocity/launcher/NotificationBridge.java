package za.co.velocity.launcher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.Intent;
import android.util.Log;

/** Receives actual local Android notifications only after the user grants access. */
public final class NotificationBridge extends NotificationListenerService {
    public static final String ACTION_CHANGED = "za.co.velocity.launcher.NOTIFICATIONS_CHANGED";
    private static volatile NotificationBridge current;

    public static StatusBarNotification[] currentNotifications() {
        NotificationBridge bridge = current;
        if (bridge == null) return new StatusBarNotification[0];
        try {
            StatusBarNotification[] all = bridge.getActiveNotifications();
            return all == null ? new StatusBarNotification[0] : all;
        } catch (Exception e) { return new StatusBarNotification[0]; }
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected(); current = this; notifyUi();
    }
    @Override public void onListenerDisconnected() {
        current = null; notifyUi(); super.onListenerDisconnected();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { notifyUi(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { notifyUi(); }

    private void notifyUi() {
        try {
            sendBroadcast(new Intent(ACTION_CHANGED).setPackage(getPackageName()));
        } catch (Exception e) { Log.w("Velocity", "Notification refresh failed", e); }
    }
}
