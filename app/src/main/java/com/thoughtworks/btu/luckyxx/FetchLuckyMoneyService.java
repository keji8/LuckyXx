package com.thoughtworks.btu.luckyxx;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.RemoteViews;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class FetchLuckyMoneyService extends AccessibilityService {
    private ArrayList<AccessibilityNodeInfo> mNodeInfoList = new ArrayList<AccessibilityNodeInfo>();
    private boolean isLuckyMoneyClicked;
    private boolean isContainsLucky;
    private boolean isContainsOpenLucky;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            unlockScreen();
            isLuckyMoneyClicked = false;

            if (Build.VERSION.SDK_INT < 18) {
                Notification notification = (Notification) event.getParcelableData();
                List<String> textList = getText(notification);
                if (null != textList && textList.size() > 0) {
                    for (String text : textList) {
                        if (!TextUtils.isEmpty(text) && text.contains("[微信红包]")) {
                            final PendingIntent pendingIntent = notification.contentIntent;
                            try {
                                pendingIntent.send();
                            } catch (PendingIntent.CanceledException e) {
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AccessibilityNodeInfo nodeInfo = event.getSource();

            if (null != nodeInfo) {
                mNodeInfoList.clear();
                traverseNode(nodeInfo);
                if (isContainsLucky && !isLuckyMoneyClicked) {
                    int size = mNodeInfoList.size();
                    if (size > 0) {
                        /** step1: get the last hongbao cell to fire click action */
                        AccessibilityNodeInfo cellNode = mNodeInfoList.get(size - 1);
                        System.out.println("alibaba"+cellNode.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                        isContainsLucky = false;
                        isLuckyMoneyClicked = true;
                    }
                }
                if (isContainsOpenLucky) {
                    int size = mNodeInfoList.size();
                    if (size > 0) {
                        /** step2: when hongbao clicked we need to open it, so fire click action */
                        AccessibilityNodeInfo cellNode = mNodeInfoList.get(size - 1);
                        cellNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        isContainsOpenLucky = false;
                    }
                }
            }
        }
    }

    private void unlockScreen() {
        Window window = LaunchActivity.context.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    private List<String> getText(Notification notification) {
        if (null == notification) return null;

        RemoteViews views = notification.bigContentView;
        if (views == null) views = notification.contentView;
        if (views == null) return null;

        // Use reflection to examine the m_actions member of the given RemoteViews object.
        // It's not pretty, but it works.
        List<String> text = new ArrayList<String>();
        try {
            Field field = views.getClass().getDeclaredField("mActions");
            field.setAccessible(true);

            @SuppressWarnings("unchecked")
            ArrayList<Parcelable> actions = (ArrayList<Parcelable>) field.get(views);

            // Find the setText() and setTime() reflection actions
            for (Parcelable p : actions) {
                Parcel parcel = Parcel.obtain();
                p.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);

                // The tag tells which type of action it is (2 is ReflectionAction, from the source)
                int tag = parcel.readInt();
                if (tag != 2) continue;

                // View ID
                parcel.readInt();

                String methodName = parcel.readString();
                if (null == methodName) {
                    continue;
                } else if (methodName.equals("setText")) {
                    // Parameter type (10 = Character Sequence)
                    parcel.readInt();

                    // Store the actual string
                    String t = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel).toString().trim();
                    text.add(t);
                }
                parcel.recycle();
            }
        } catch (Exception e) {
        }

        return text;
    }

    private void traverseNode(AccessibilityNodeInfo node) {
        if (null == node) return;

        final int count = node.getChildCount();
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                AccessibilityNodeInfo childNode = node.getChild(i);
                traverseNode(childNode);
            }
        } else {
            CharSequence text = node.getText();
            if (null != text && text.length() > 0) {
                String str = text.toString();
                if (str.contains("领取红包")) {
                    isContainsLucky = true;
                    AccessibilityNodeInfo cellNode = node.getParent().getParent().getParent().getParent();
                    if (null != cellNode) mNodeInfoList.add(cellNode);
                }

                if (str.contains("拆红包")) {
                    isContainsOpenLucky = true;
                    mNodeInfoList.add(node);
                }
            }
        }
    }

    @Override
    public void onInterrupt() {

    }

}
