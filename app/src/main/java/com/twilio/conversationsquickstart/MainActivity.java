package com.twilio.conversationsquickstart;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.twilio.conversations.Message;

import org.jetbrains.annotations.Nullable;

public class MainActivity extends AppCompatActivity implements QuickstartConversationsManagerListener {

    public final static String TAG = "TwilioConversations";

    // Update this identity for each individual user, for instance after they login
    private String identity = "CONVERSATIONS_USER";

    private MessagesAdapter messagesAdapter;

    private EditText writeMessageEditText;

    private final QuickstartConversationsManager quickstartConversationsManager = new QuickstartConversationsManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        quickstartConversationsManager.setListener(this);

        RecyclerView recyclerView = findViewById(R.id.messagesRecyclerView);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);

        // for a chat app, show latest messages at the bottom
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);

        messagesAdapter = new MessagesAdapter();
        recyclerView.setAdapter(messagesAdapter);

        writeMessageEditText = findViewById(R.id.writeMessageEditText);


        Button sendChatMessageButton = findViewById(R.id.sendChatMessageButton);
        sendChatMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String messageBody = writeMessageEditText.getText().toString();
                if (messageBody.length() > 0) {
                    quickstartConversationsManager.sendMessage(messageBody);
                }
            }
        });

        // Token Method 1 - supplied from strings.xml as the test_access_token
        quickstartConversationsManager.initializeWithAccessToken(this, getString(R.string.test_access_token));

        // Token Method 2 - retrieve the access token from a web server or Twilio Function
        //retrieveTokenFromServer();
    }

    private void retrieveTokenFromServer() {
        quickstartConversationsManager.retrieveAccessTokenFromServer(this, identity, new TokenResponseListener() {
            @Override
            public void receivedTokenResponse(boolean success, @Nullable Exception exception) {
                if (success) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // need to modify user interface elements on the UI thread
                            setTitle(identity);
                        }
                    });
                }
                else {
                    String errorMessage = getString(R.string.error_retrieving_access_token);
                    if (exception != null) {
                        errorMessage = errorMessage + " " + exception.getLocalizedMessage();
                    }
                    Toast.makeText(MainActivity.this,
                            errorMessage,
                            Toast.LENGTH_LONG)
                            .show();
                }
            }
        });
    }

    @Override
    public void receivedNewMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // need to modify user interface elements on the UI thread
                messagesAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void reloadMessages() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // need to modify user interface elements on the UI thread
                messagesAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void messageSentCallback() {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // need to modify user interface elements on the UI thread
                writeMessageEditText.setText("");
            }
        });
    }

    class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {

            final TextView messageTextView;

            ViewHolder(TextView textView) {
                super(textView);
                messageTextView = textView;
            }
        }

        MessagesAdapter() {

        }

        @NonNull
        @Override
        public MessagesAdapter
                .ViewHolder onCreateViewHolder(ViewGroup parent,
                                               int viewType) {
            TextView messageTextView = (TextView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.message_text_view, parent, false);
            return new ViewHolder(messageTextView);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Message message = quickstartConversationsManager.getMessages().get(position);
            String messageText = String.format("%s: %s", message.getAuthor(), message.getMessageBody());
            holder.messageTextView.setText(messageText);
        }

        @Override
        public int getItemCount() {
            return quickstartConversationsManager.getMessages().size();
        }
    }
}
