package org.infobip.mobile.messaging.demo;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.infobip.mobile.messaging.api.chat.WidgetInfo;
import org.infobip.mobile.messaging.chat.core.InAppChatWidgetView;
import org.infobip.mobile.messaging.chat.core.widget.LivechatWidgetMessage;
import org.infobip.mobile.messaging.chat.core.widget.LivechatWidgetResult;
import org.infobip.mobile.messaging.chat.core.widget.LivechatWidgetThread;
import org.infobip.mobile.messaging.chat.core.widget.LivechatWidgetThreads;
import org.infobip.mobile.messaging.chat.core.widget.LivechatWidgetView;
import org.infobip.mobile.messaging.chat.models.MessagePayload;
import org.infobip.mobile.messaging.chat.view.InAppChatFragment;
import org.infobip.mobile.messaging.util.StringUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import kotlin.Unit;

public class InAppChatFragmentDemoFragment extends Fragment {

    static final String ARG_WITH_TOOLBAR = "InAppChatFragmentDemoFragment.ARG_WITH_TOOLBAR";
    static final String ARG_WITH_INPUT = "InAppChatFragmentDemoFragment.ARG_WITH_INPUT";

    private View rootView;
    private FragmentContainerView inAppChatFragmentContainer;
    private Button navigateBackButton, sendButton;
    private Group inputGroup;
    private CheckBox toolbarCheckBox, inputCheckBox;
    private TextInputEditText messageInput;
    /**
     * Set to true if you want to use InAppChatFragment's ActionBar
     */
    private boolean withInAppChatToolbar = true;
    /**
     * Set to true if you want to use InAppChatFragment's MessageInput
     */
    private boolean withInAppChatInput = false;
    private InAppChatFragment fragment;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_in_app_chat_fragment_demo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;
        inAppChatFragmentContainer = rootView.findViewById(R.id.chatFragmentContainer);
        navigateBackButton = rootView.findViewById(R.id.navigateBack);
        sendButton = rootView.findViewById(R.id.send);
        messageInput = rootView.findViewById(R.id.messageInput);
        inputGroup = rootView.findViewById(R.id.chatInputGroup);
        toolbarCheckBox = rootView.findViewById(R.id.toolbarCheckbox);
        inputCheckBox = rootView.findViewById(R.id.inputCheckbox);

        Bundle args = getArguments();
        if (args != null) {
            withInAppChatToolbar = args.getBoolean(ARG_WITH_TOOLBAR, withInAppChatToolbar);
            withInAppChatInput = args.getBoolean(ARG_WITH_INPUT, withInAppChatInput);
        }

        setUpInAppChatFragment(withInAppChatToolbar, withInAppChatInput);
        updateCustomToolbar(withInAppChatToolbar); //Use your custom ActionBar/navigation UI if you don't use InAppChatFragment's ActionBar
        updateCustomMessageInput(withInAppChatInput); //Use your custom UI to compose message if you don't use InAppChatFragment's MessageInput
        setUpCheckboxes();
    }

    //region InAppChatFragment
    private InAppChatFragment getInAppChatFragment() {
        if (fragment == null) {
            fragment = new InAppChatFragment();
        }
        return fragment;
    }

    private InAppChatFragment.EventsListener createEventsListener(boolean withInAppChatToolbar) {
        return new InAppChatFragment.EventsListener() {

            @Override
            public void onChatWidgetThemeChanged(@NonNull LivechatWidgetResult<String> result) {
                //Widget theme was applied
            }

            @Override
            public void onChatLanguageChanged(@NonNull LivechatWidgetResult<String> result) {
                //Widget language was changed
            }

            @Override
            public void onChatThreadCreated(@NonNull LivechatWidgetResult<? extends LivechatWidgetMessage> result) {
                //Chat thread was created
            }

            @Override
            public void onChatThreadListShown(@NonNull LivechatWidgetResult<Unit> result) {
                //Chat thread list was shown
            }

            @Override
            public void onChatThreadShown(@NonNull LivechatWidgetResult<LivechatWidgetThread> result) {
                //Chat thread was shown
            }

            @Override
            public void onChatActiveThreadReceived(@NonNull LivechatWidgetResult<LivechatWidgetThread> result) {
                //Active chat thread was received
            }

            @Override
            public void onChatThreadsReceived(@NonNull LivechatWidgetResult<LivechatWidgetThreads> result) {
                //Chat threads were received
            }

            @Override
            public void onChatContextualDataSent(@NonNull LivechatWidgetResult<String> result) {
                //Contextual data was sent
            }

            @Override
            public void onChatDraftSent(@NonNull LivechatWidgetResult<String> result) {
                //Deprecated, use onChatSent(LivechatWidgetResult<? extends LivechatWidgetMessage> result) instead
            }

            @Override
            public void onChatMessageSent(@NonNull LivechatWidgetResult<String> result) {
                //Deprecated, use onChatSent(LivechatWidgetResult<? extends LivechatWidgetMessage> result) instead
            }

            @Override
            public void onChatSent(@NonNull LivechatWidgetResult<? extends LivechatWidgetMessage> result) {
                //Message was sent
            }

            @Override
            public void onChatConnectionResumed(@NonNull LivechatWidgetResult<Unit> result) {
                //Chat connection was resumed
            }

            @Override
            public void onChatConnectionPaused(@NonNull LivechatWidgetResult<Unit> result) {
                //Chat connection was paused
            }

            @Override
            public void onChatLoadingFinished(@NonNull LivechatWidgetResult<Unit> result) {
                //Chat was loaded, if result.isSuccess() = true there was no error
            }

            @Override
            public boolean onChatAttachmentPreviewOpened(@Nullable String url, @Nullable String type, @Nullable String caption) {
                //Handle attachment preview
                return false;
            }

            @Override
            public void onChatRawMessageReceived(@NonNull String rawMessage) {
                //You can use raw message for further processing on your side
            }

            @Override
            public void onChatWidgetThemeChanged(@NonNull String widgetThemeName) {
                //Deprecated, use onChatWidgetThemeChanged(LivechatWidgetResult<String> result) instead
            }

            @Override
            public void onChatWidgetInfoUpdated(@NonNull WidgetInfo widgetInfo) {
                //Useful livechat widget information
            }

            @Override
            public void onChatViewChanged(@NonNull LivechatWidgetView widgetView) {
                //Handle message input multithread livechat widget if don't use InAppChatFragment's MessageInput
                switch (widgetView) {
                    case LOADING:
                    case THREAD_LIST:
                    case LOADING_THREAD:
                    case CLOSED_THREAD:
                        sendButton.setEnabled(false);
                        break;
                    case SINGLE_MODE_THREAD:
                    case THREAD:
                        sendButton.setEnabled(true);
                        break;
                }
            }

            @Override
            public void onChatViewChanged(@NonNull InAppChatWidgetView widgetView) {
                //Deprecated, use onChatViewChanged(LivechatWidgetView widgetView) instead
            }

            @Override
            public boolean onAttachmentPreviewOpened(@Nullable String url, @Nullable String type, @Nullable String caption) {
                //Deprecated, use onChatAttachmentPreviewOpened(String url, String type, String caption) instead
                return false;
            }

            @Override
            public void onChatControlsVisibilityChanged(boolean isVisible) {
                //Handle chat controls visibility change, you can show/hide input based on isVisible value
            }

            @Override
            public void onChatLoaded(boolean controlsEnabled) {
                //Deprecated, use onChatLoadingFinished(LivechatWidgetResult<Unit> result) instead
            }

            @Override
            public void onChatDisconnected() {
                //Deprecated, use onChatConnectionPaused(LivechatWidgetResult<Unit> result) instead
            }

            @Override
            public void onChatReconnected() {
                //Deprecated, use onChatConnectionResumed(LivechatWidgetResult<Unit> result) instead
            }

            @Override
            public void onExitChatPressed() {
                if (withInAppChatToolbar) {
                    showActionBar();
                }
                FragmentTransaction fragmentTransaction = InAppChatFragmentDemoFragment.this.getParentFragmentManager().beginTransaction();
                fragmentTransaction.remove(InAppChatFragmentDemoFragment.this);
                fragmentTransaction.commit();
            }
        };
    }

    private void updateInAppChatFragment(
            boolean withInAppChatToolbar,
            boolean withInAppChatInput
    ) {
        InAppChatFragment fragment = getInAppChatFragment();
        fragment.setEventsListener(createEventsListener(withInAppChatToolbar));
        if (fragment.getWithToolbar() != withInAppChatToolbar) {
            fragment.setWithToolbar(withInAppChatToolbar);
        }
        if (fragment.getWithInput() != withInAppChatInput) {
            fragment.setWithInput(withInAppChatInput);
        }
    }

    private void setUpInAppChatFragment(
            boolean withInAppChatToolbar,
            boolean withInAppChatInput
    ) {
        InAppChatFragment fragment = getInAppChatFragment();
        updateInAppChatFragment(withInAppChatToolbar, withInAppChatInput);
        fragment.setErrorsHandler(new InAppChatFragment.ErrorsHandler() {

            @Override
            public void handlerError(@NonNull String error) {
                //Your custom handling of general error or use default handler
                fragment.getDefaultErrorsHandler().handlerError(error);
            }

            @Override
            public void handlerWidgetError(@NonNull String error) {
                //Your custom handling of Livechat widget error or use default handler
                fragment.getDefaultErrorsHandler().handlerWidgetError(error);
            }

            @Override
            public void handlerNoInternetConnectionError(boolean hasConnection) {
                //Your custom handling of missing network connection error or use default handler
                fragment.getDefaultErrorsHandler().handlerNoInternetConnectionError(hasConnection);
            }

        });
        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.replace(inAppChatFragmentContainer.getId(), fragment);
        fragmentTransaction.commit();
        fm.executePendingTransactions();
    }
    //endregion

    //region Custom Toolbar
    private void updateCustomToolbar(boolean withInAppChatToolbar) {
        navigateBackButton.setVisibility(withInAppChatToolbar ? View.GONE : View.VISIBLE);
        if (withInAppChatToolbar) {
            hideActionBar();
        }
        else {
            setUpNavigateBackButton();
        }
    }

    private void setUpNavigateBackButton() {
        navigateBackButton.setOnClickListener((v) -> getInAppChatFragment().navigateBackOrCloseChat());
    }

    private ActionBar getActivityActionBar() {
        Activity activity = requireActivity();
        if (activity instanceof AppCompatActivity) {
            return ((AppCompatActivity) activity).getSupportActionBar();
        }
        return null;
    }

    private void hideActionBar() {
        ActionBar activityActionBar = getActivityActionBar();
        if (activityActionBar != null) {
            activityActionBar.hide();
        }
    }

    private void showActionBar() {
        ActionBar activityActionBar = getActivityActionBar();
        if (activityActionBar != null) {
            activityActionBar.show();
        }
    }
    //endregion

    //region Custom MessageInput
    private void updateCustomMessageInput(boolean withInAppChatInput) {
        inputGroup.setVisibility(withInAppChatInput ? View.GONE : View.VISIBLE);
        if (!withInAppChatInput) {
            setUpMessageInput();
        }
    }

    private void setUpMessageInput() {
        sendButton.setOnClickListener(v -> {
            Editable text = messageInput.getText();
            if (text != null) {
                String message = text.toString();
                if (StringUtils.isNotBlank(message)) {
                    try {
                        getInAppChatFragment().send(new MessagePayload.Basic(message));
                    } catch (IllegalArgumentException e) {
                        String error = e.getMessage();
                        if (StringUtils.isNotBlank(error)) {
                            Snackbar.make(rootView, error, Snackbar.LENGTH_SHORT).show();
                        }
                    }
                    text.clear();
                }
            }
        });
    }
    //endregion

    private void setUpCheckboxes() {
        toolbarCheckBox.setChecked(withInAppChatToolbar);
        toolbarCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            withInAppChatToolbar = isChecked;
            updateCustomToolbar(withInAppChatToolbar);
            updateInAppChatFragment(withInAppChatToolbar, withInAppChatInput);
            Bundle arguments = getArguments();
            if (arguments != null) {
                arguments.putBoolean(ARG_WITH_TOOLBAR, withInAppChatToolbar);
            }
        });
        inputCheckBox.setChecked(withInAppChatInput);
        inputCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            withInAppChatInput = isChecked;
            updateCustomMessageInput(withInAppChatInput);
            updateInAppChatFragment(withInAppChatToolbar, withInAppChatInput);
            Bundle arguments = getArguments();
            if (arguments != null) {
                arguments.putBoolean(ARG_WITH_INPUT, withInAppChatInput);
            }
        });
    }

}
