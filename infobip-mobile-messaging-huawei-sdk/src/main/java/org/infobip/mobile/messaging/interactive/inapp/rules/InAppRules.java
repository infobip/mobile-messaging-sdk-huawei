package org.infobip.mobile.messaging.interactive.inapp.rules;

import org.infobip.mobile.messaging.Message;
import org.infobip.mobile.messaging.interactive.MobileInteractive;
import org.infobip.mobile.messaging.interactive.NotificationAction;
import org.infobip.mobile.messaging.interactive.NotificationCategory;
import org.infobip.mobile.messaging.interactive.inapp.foreground.ForegroundState;
import org.infobip.mobile.messaging.interactive.inapp.foreground.ForegroundStateMonitor;
import org.infobip.mobile.messaging.interactive.predefined.PredefinedActionsProvider;
import org.infobip.mobile.messaging.platform.Time;
import org.infobip.mobile.messaging.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author sslavin
 * @since 15/04/2018.
 */
public class InAppRules {

    private final MobileInteractive mobileInteractive;
    private final ForegroundStateMonitor foregroundStateMonitor;
    private final NotificationAction[] defaultInAppActions;

    public InAppRules(MobileInteractive mobileInteractive, ForegroundStateMonitor foregroundStateMonitor, PredefinedActionsProvider predefinedActionsProvider) {
        this.mobileInteractive = mobileInteractive;
        this.foregroundStateMonitor = foregroundStateMonitor;
        this.defaultInAppActions = predefinedActionsProvider.getDefaultInAppActions();
    }

    public ShowOrNot shouldDisplayDialogFor(Message message) {
        long inAppExpiryTimestamp = message.getInAppExpiryTimestamp();
        if (!hasInAppEnabled(message) || (inAppExpiryTimestamp != 0 && inAppExpiryTimestamp < Time.now())) {
            return ShowOrNot.not();
        }

        ForegroundState state = foregroundStateMonitor.isInForeground();
        if (state.isForeground() && state.getForegroundActivity() != null && !state.getForegroundActivity().isFinishing()) {
            if (StringUtils.isBlank(message.getCategory())) {
                return ShowOrNot.showNowWithDefaultActions(state.getForegroundActivity(), defaultInAppActions);
            }

            NotificationCategory category = mobileInteractive.getNotificationCategory(message.getCategory());
            if (category == null || category.getNotificationActions() == null) {
                return ShowOrNot.showNowWithDefaultActions(state.getForegroundActivity(), defaultInAppActions);
            }

            List<NotificationAction> actions = Arrays.asList(category.getNotificationActions());
            if (actions.size() == 0) {
                return ShowOrNot.showNowWithDefaultActions(state.getForegroundActivity(), defaultInAppActions);
            }

            NotificationAction[] eligibleActions = filterActionsForInAppDialog(category.getNotificationActions());
            if (eligibleActions.length == 0) {
                return ShowOrNot.showNowWithDefaultActions(state.getForegroundActivity(), defaultInAppActions);
            }

            return ShowOrNot.showNow(category, eligibleActions, state.getForegroundActivity());
        } else {
            return ShowOrNot.showWhenInForeground();
        }
    }

    private NotificationAction[] filterActionsForInAppDialog(NotificationAction[] actions) {
        List<NotificationAction> as = new ArrayList<>();
        for (NotificationAction action : actions) {
            // remove "input" actions
            if (!action.hasInput()) {
                as.add(action);
            }
        }
        return as.toArray(new NotificationAction[0]);
    }

    private static boolean hasInAppEnabled(Message message) {
        return message.getInAppStyle() == Message.InAppStyle.MODAL;
    }
}
