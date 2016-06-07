package com.twilio.ipmessagingchannels;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.twilio.common.TwilioAccessManager;
import com.twilio.common.TwilioAccessManagerFactory;
import com.twilio.common.TwilioAccessManagerListener;
import com.twilio.ipmessaging.Channel;
import com.twilio.ipmessaging.ChannelListener;
import com.twilio.ipmessaging.Constants;
import com.twilio.ipmessaging.ErrorInfo;
import com.twilio.ipmessaging.Member;
import com.twilio.ipmessaging.Message;
import com.twilio.ipmessaging.TwilioIPMessagingClient;
import com.twilio.ipmessaging.TwilioIPMessagingSDK;

import java.util.HashMap;
import java.util.Map;

public class MessagingActivity extends AppCompatActivity {

    /*
       Change this URL to match the token URL for your quick start server

       Download the quick start server from:

       https://www.twilio.com/docs/api/ip-messaging/guides/quickstart-js
    */
    final static String SERVER_TOKEN_URL = "http://localhost:8000/token.php";

    final static String DEFAULT_CHANNEL_NAME = "channels2";
    final static String TAG = "TwilioIPMessaging";



    private TwilioAccessManager mAccessManager;
    private TwilioIPMessagingClient mMessagingClient;

    private Channel mGeneralChannel;

    private TextView mMessagesTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messaging);

        retrieveAccessTokenfromServer();

        mMessagesTextView = (TextView) findViewById(R.id.messagesTextView);

    }

    private void retrieveAccessTokenfromServer() {
        Ion.with(this)
                .load(SERVER_TOKEN_URL)
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception e, JsonObject result) {
                        if (e == null) {
                            String identity = result.get("identity").getAsString();
                            String accessToken = result.get("token").getAsString();

                            setTitle(identity);

                            mAccessManager = TwilioAccessManagerFactory.createAccessManager(accessToken,
                                    mAccessManagerListener);

                            TwilioIPMessagingClient.Properties props =
                                    new TwilioIPMessagingClient.Properties(
                                            TwilioIPMessagingClient.SynchronizationStrategy.ALL, 500);

                            mMessagingClient = TwilioIPMessagingSDK.createClient(mAccessManager, props,
                                    mMessagingClientCallback);

                            mMessagingClient.getChannels().loadChannelsWithListener(
                                    new Constants.StatusListener() {
                                        @Override
                                        public void onSuccess() {
                                            final Channel defaultChannel = mMessagingClient.getChannels()
                                                    .getChannelByUniqueName(DEFAULT_CHANNEL_NAME);
                                            if (defaultChannel != null) {
                                                joinChannel(defaultChannel);
                                            } else {
                                                Map<String, Object> channelProps = new HashMap<>();
                                                channelProps.put(Constants.CHANNEL_FRIENDLY_NAME,"General Chat Channel");
                                                channelProps.put(Constants.CHANNEL_UNIQUE_NAME,DEFAULT_CHANNEL_NAME);
                                                channelProps.put(Constants.CHANNEL_TYPE,Channel.ChannelType.CHANNEL_TYPE_PUBLIC);
                                                mMessagingClient.getChannels().createChannel(channelProps, new Constants.CreateChannelListener() {
                                                    @Override
                                                    public void onCreated(final Channel channel) {
                                                        if (channel != null) {
                                                            Log.d(TAG, "Created default channel");
                                                            MessagingActivity.this.runOnUiThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    joinChannel(channel);
                                                                }
                                                            });
                                                        }
                                                    }

                                                    @Override
                                                    public void onError(ErrorInfo errorInfo) {
                                                        Log.e(TAG,"Error creating channel: " + errorInfo.getErrorText());
                                                    }
                                                });
                                            }
                                        }
                                    });
                        } else {
                            Toast.makeText(MessagingActivity.this,
                                    R.string.error_retrieving_access_token, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });
    }

    private void joinChannel(final Channel channel) {
        Log.d(TAG, "Joining Channel: " + channel.getUniqueName());
        channel.join(new Constants.StatusListener() {
            @Override
            public void onSuccess() {
                mGeneralChannel = channel;
                Log.d(TAG, "Joined default channel");
                mGeneralChannel.setListener(mDefaultChannelListener);
                sendMessage("Test Message");
            }

            @Override
            public void onError(ErrorInfo errorInfo) {
                Log.e(TAG,"Error joining channel: " + errorInfo.getErrorText());
            }
        });
    }

    private void sendMessage(String messageBody) {
        Message message = mGeneralChannel.getMessages().createMessage(messageBody);
        Log.d(TAG,"Message created");
        mGeneralChannel.getMessages().sendMessage(message, new Constants.StatusListener() {
            @Override
            public void onSuccess() {
               Log.d(TAG,"Message sent.");

            }

            @Override
            public void onError(ErrorInfo errorInfo) {
                Log.e(TAG,"Error sending message: " + errorInfo.getErrorText());
            }
        });
        }

    private TwilioAccessManagerListener mAccessManagerListener = new TwilioAccessManagerListener() {
        @Override
        public void onTokenExpired(TwilioAccessManager twilioAccessManager) {
            Log.d(TAG, "Access token has expired");
        }

        @Override
        public void onTokenUpdated(TwilioAccessManager twilioAccessManager) {
            Log.d(TAG, "Access token has updated");
        }

        @Override
        public void onError(TwilioAccessManager twilioAccessManager, String errorMessage) {
            Log.d(TAG, "Error with Twilio Access Manager: " + errorMessage);
        }
    };

    private Constants.CallbackListener<TwilioIPMessagingClient> mMessagingClientCallback =
            new Constants.CallbackListener<TwilioIPMessagingClient>() {
                @Override
                public void onSuccess(TwilioIPMessagingClient twilioIPMessagingClient) {
                    Log.d(TAG, "Success creating Twilio IP Messaging Client");
                }
            };

    private ChannelListener mDefaultChannelListener = new ChannelListener() {
        @Override
        public void onMessageAdd(final Message message) {
            Log.d(TAG, "Message added: " + message.getMessageBody());
            mMessagesTextView.setText(message.getAuthor() + ":" + message.getMessageBody());
        }

        @Override
        public void onMessageChange(Message message) {
            Log.d(TAG, "Message changed: " + message.getMessageBody());
        }

        @Override
        public void onMessageDelete(Message message) {
            Log.d(TAG, "Message deleted");
        }

        @Override
        public void onMemberJoin(Member member) {
            Log.d(TAG, "Member joined: " + member.getUserInfo().getIdentity());
        }

        @Override
        public void onMemberChange(Member member) {
            Log.d(TAG, "Member changed: " + member.getUserInfo().getIdentity());
        }

        @Override
        public void onMemberDelete(Member member) {
            Log.d(TAG, "Member deleted: " + member.getUserInfo().getIdentity());
        }

        @Override
        public void onAttributesChange(Map<String, String> map) {
            Log.d(TAG, "Attributes changed: " + map.toString());
        }

        @Override
        public void onTypingStarted(Member member) {
            Log.d(TAG, "Started Typing: " + member.getUserInfo().getIdentity());
        }

        @Override
        public void onTypingEnded(Member member) {
            Log.d(TAG, "Ended Typing: " + member.getUserInfo().getIdentity());
        }

        @Override
        public void onSynchronizationChange(Channel channel) {

        }
    };




}
