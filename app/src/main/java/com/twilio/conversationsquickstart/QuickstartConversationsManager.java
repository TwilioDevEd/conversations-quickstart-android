package com.twilio.conversationsquickstart;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.twilio.conversations.CallbackListener;
import com.twilio.conversations.Conversation;
import com.twilio.conversations.ConversationListener;
import com.twilio.conversations.ConversationsClient;
import com.twilio.conversations.ConversationsClientListener;
import com.twilio.conversations.ErrorInfo;
import com.twilio.conversations.Participant;
import com.twilio.conversations.Message;
import com.twilio.conversations.StatusListener;
import com.twilio.conversations.User;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

interface QuickstartConversationsManagerListener {
    void receivedNewMessage();
    void messageSentCallback();
    void reloadMessages();
}

interface TokenResponseListener {
    void receivedTokenResponse(boolean success, @Nullable Exception exception);
}

interface AccessTokenListener {
    void receivedAccessToken(@Nullable String token, @Nullable Exception exception);
}


class QuickstartConversationsManager {

    // This is the unique name of the conversation  we are using
    private final static String DEFAULT_CONVERSATION_NAME = "general";

    final private ArrayList<Message> messages = new ArrayList<>();

    private ConversationsClient conversationsClient;

    private Conversation conversation;

    private QuickstartConversationsManagerListener conversationsManagerListener;

    private String tokenURL = "";

    private class TokenResponse {
        String token;
    }

    void retrieveAccessTokenFromServer(final Context context, String identity,
                                       final TokenResponseListener listener) {

        // Set the chat token URL in your strings.xml file
        String chatTokenURL = context.getString(R.string.chat_token_url);

        if ("https://YOUR_DOMAIN_HERE.twil.io/chat-token".equals(chatTokenURL)) {
            listener.receivedTokenResponse(false, new Exception("You need to replace the chat token URL in strings.xml"));
            return;
        }

        tokenURL = chatTokenURL + "?identity=" + identity;

        new Thread(new Runnable() {
            @Override
            public void run() {
                retrieveToken(new AccessTokenListener() {
                    @Override
                    public void receivedAccessToken(@Nullable String token,
                                                    @Nullable Exception exception) {
                        if (token != null) {
                            ConversationsClient.Properties props = ConversationsClient.Properties.newBuilder().createProperties();
                            ConversationsClient.create(context, token, props, mConversationsClientCallback);
                            listener.receivedTokenResponse(true,null);
                        } else {
                            listener.receivedTokenResponse(false, exception);
                        }
                    }
                });
            }
        }).start();
    }

    void initializeWithAccessToken(final Context context, final String token) {

        ConversationsClient.Properties props = ConversationsClient.Properties.newBuilder().createProperties();
        ConversationsClient.create(context, token, props, mConversationsClientCallback);
    }

    private void retrieveToken(AccessTokenListener listener) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(tokenURL)
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = "";
            if (response != null && response.body() != null) {
                responseBody = response.body().string();
            }
            Log.d(MainActivity.TAG, "Response from server: " + responseBody);
            Gson gson = new Gson();
            TokenResponse tokenResponse = gson.fromJson(responseBody,TokenResponse.class);
            String accessToken = tokenResponse.token;
            Log.d(MainActivity.TAG, "Retrieved access token from server: " + accessToken);
            listener.receivedAccessToken(accessToken, null);

        }
        catch (IOException ex) {
            Log.e(MainActivity.TAG, ex.getLocalizedMessage(),ex);
            listener.receivedAccessToken(null, ex);
        }
    }

    void sendMessage(String messageBody) {
        if (conversation != null) {
            Message.Options options = Message.options().withBody(messageBody);
            Log.d(MainActivity.TAG,"Message created");
            conversation.sendMessage(options, new CallbackListener<Message>() {
                @Override
                public void onSuccess(Message message) {
                    if (conversationsManagerListener != null) {
                        conversationsManagerListener.messageSentCallback();
                    }
                }
            });
        }
    }


    private void loadChannels() {
        if (conversationsClient == null || conversationsClient.getMyConversations() == null) {
            return;
        }
        conversationsClient.getConversation(DEFAULT_CONVERSATION_NAME, new CallbackListener<Conversation>() {
            @Override
            public void onSuccess(Conversation conversation) {
                if (conversation != null) {
                    if (conversation.getStatus() == Conversation.ConversationStatus.JOINED
                            || conversation.getStatus() == Conversation.ConversationStatus.NOT_PARTICIPATING) {
                        Log.d(MainActivity.TAG, "Already Exists in Conversation: " + DEFAULT_CONVERSATION_NAME);
                        QuickstartConversationsManager.this.conversation = conversation;
                        QuickstartConversationsManager.this.conversation.addListener(mDefaultConversationListener);
                        QuickstartConversationsManager.this.loadPreviousMessages(conversation);
                    } else {
                        Log.d(MainActivity.TAG, "Joining Conversation: " + DEFAULT_CONVERSATION_NAME);
                        joinConversation(conversation);
                    }
                }
            }

            @Override
            public void onError(ErrorInfo errorInfo) {
                Log.e(MainActivity.TAG, "Error retrieving conversation: " + errorInfo.getMessage());
                createConversation();
            }

        });
    }

    private void createConversation() {
        Log.d(MainActivity.TAG, "Creating Conversation: " + DEFAULT_CONVERSATION_NAME);

        conversationsClient.createConversation(DEFAULT_CONVERSATION_NAME,
                new CallbackListener<Conversation>() {
                    @Override
                    public void onSuccess(Conversation conversation) {
                        if (conversation != null) {
                            Log.d(MainActivity.TAG, "Joining Conversation: " + DEFAULT_CONVERSATION_NAME);
                            joinConversation(conversation);
                        }
                    }

                    @Override
                    public void onError(ErrorInfo errorInfo) {
                        Log.e(MainActivity.TAG, "Error creating conversation: " + errorInfo.getMessage());
                    }
                });
    }


    private void joinConversation(final Conversation conversation) {
        Log.d(MainActivity.TAG, "Joining Conversation: " + conversation.getUniqueName());
        if (conversation.getStatus() == Conversation.ConversationStatus.JOINED) {

            QuickstartConversationsManager.this.conversation = conversation;
            Log.d(MainActivity.TAG, "Already joined default conversation");
            QuickstartConversationsManager.this.conversation.addListener(mDefaultConversationListener);
            return;
        }


        conversation.join(new StatusListener() {
            @Override
            public void onSuccess() {
                QuickstartConversationsManager.this.conversation = conversation;
                Log.d(MainActivity.TAG, "Joined default conversation");
                QuickstartConversationsManager.this.conversation.addListener(mDefaultConversationListener);
                QuickstartConversationsManager.this.loadPreviousMessages(conversation);
            }

            @Override
            public void onError(ErrorInfo errorInfo) {
                Log.e(MainActivity.TAG, "Error joining conversation: " + errorInfo.getMessage());
            }
        });
    }

    private void loadPreviousMessages(final Conversation conversation) {
        conversation.getLastMessages(100,
                new CallbackListener<List<Message>>() {
                    @Override
                    public void onSuccess(List<Message> result) {
                        messages.addAll(result);
                        if (conversationsManagerListener != null) {
                            conversationsManagerListener.reloadMessages();
                        }
                    }
                });
    }

    private final ConversationsClientListener mConversationsClientListener =
            new ConversationsClientListener() {

                @Override
                public void onConversationAdded(Conversation conversation) {

                }

                @Override
                public void onConversationUpdated(Conversation conversation, Conversation.UpdateReason updateReason) {

                }

                @Override
                public void onConversationDeleted(Conversation conversation) {

                }

                @Override
                public void onConversationSynchronizationChange(Conversation conversation) {

                }

                @Override
                public void onError(ErrorInfo errorInfo) {

                }

                @Override
                public void onUserUpdated(User user, User.UpdateReason updateReason) {

                }

                @Override
                public void onUserSubscribed(User user) {

                }

                @Override
                public void onUserUnsubscribed(User user) {

                }

                @Override
                public void onClientSynchronization(ConversationsClient.SynchronizationStatus synchronizationStatus) {
                    if (synchronizationStatus == ConversationsClient.SynchronizationStatus.COMPLETED) {
                        loadChannels();
                    }
                }

                @Override
                public void onNewMessageNotification(String s, String s1, long l) {

                }

                @Override
                public void onAddedToConversationNotification(String s) {

                }

                @Override
                public void onRemovedFromConversationNotification(String s) {

                }

                @Override
                public void onNotificationSubscribed() {

                }

                @Override
                public void onNotificationFailed(ErrorInfo errorInfo) {

                }

                @Override
                public void onConnectionStateChange(ConversationsClient.ConnectionState connectionState) {

                }

                @Override
                public void onTokenExpired() {

                }

                @Override
                public void onTokenAboutToExpire() {
                    retrieveToken(new AccessTokenListener() {
                        @Override
                        public void receivedAccessToken(@Nullable String token, @Nullable Exception exception) {
                            if (token != null) {
                                conversationsClient.updateToken(token, new StatusListener() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(MainActivity.TAG, "Refreshed access token.");
                                    }
                                });
                            }
                        }
                    });
                }
            };

    private final CallbackListener<ConversationsClient> mConversationsClientCallback =
            new CallbackListener<ConversationsClient>() {
                @Override
                public void onSuccess(ConversationsClient conversationsClient) {
                    QuickstartConversationsManager.this.conversationsClient = conversationsClient;
                    conversationsClient.addListener(QuickstartConversationsManager.this.mConversationsClientListener);
                    Log.d(MainActivity.TAG, "Success creating Twilio Conversations Client");
                }

                @Override
                public void onError(ErrorInfo errorInfo) {
                    Log.e(MainActivity.TAG, "Error creating Twilio Conversations Client: " + errorInfo.getMessage());
                }
            };


    private final ConversationListener mDefaultConversationListener = new ConversationListener() {


        @Override
        public void onMessageAdded(final Message message) {
            Log.d(MainActivity.TAG, "Message added");
            messages.add(message);
            if (conversationsManagerListener != null) {
                conversationsManagerListener.receivedNewMessage();
            }
        }

        @Override
        public void onMessageUpdated(Message message, Message.UpdateReason updateReason) {
            Log.d(MainActivity.TAG, "Message updated: " + message.getMessageBody());
        }

        @Override
        public void onMessageDeleted(Message message) {
            Log.d(MainActivity.TAG, "Message deleted");
        }

        @Override
        public void onParticipantAdded(Participant participant) {
            Log.d(MainActivity.TAG, "Participant added: " + participant.getIdentity());
        }

        @Override
        public void onParticipantUpdated(Participant participant, Participant.UpdateReason updateReason) {
            Log.d(MainActivity.TAG, "Participant updated: " + participant.getIdentity() + " " + updateReason.toString());
        }

        @Override
        public void onParticipantDeleted(Participant participant) {
            Log.d(MainActivity.TAG, "Participant deleted: " + participant.getIdentity());
        }

        @Override
        public void onTypingStarted(Conversation conversation, Participant participant) {
            Log.d(MainActivity.TAG, "Started Typing: " + participant.getIdentity());
        }

        @Override
        public void onTypingEnded(Conversation conversation, Participant participant) {
            Log.d(MainActivity.TAG, "Ended Typing: " + participant.getIdentity());
        }

        @Override
        public void onSynchronizationChanged(Conversation conversation) {

        }
    };

    public ArrayList<Message> getMessages() {
        return messages;
    }

    public void setListener(QuickstartConversationsManagerListener listener)  {
        this.conversationsManagerListener = listener;
    }
}

