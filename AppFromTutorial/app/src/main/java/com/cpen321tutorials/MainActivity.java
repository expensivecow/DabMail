package com.cpen321tutorials;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";

    private CallbackManager callbackManager;

    private LoginButton loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        callbackManager = CallbackManager.Factory.create();

        loginButton = (LoginButton) findViewById(R.id.login_button);
        loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d(TAG, "User has successfully logged in");

                Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                startActivity(intent);
                MainActivity.this.finish();
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "User has cancelled the login process");
            }

            @Override
            public void onError(FacebookException exception) {
                Log.d(TAG, "Oh no. You have no network or some other problem");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        callbackManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }
    @Override
    protected void onStart() {

        super.onStart();

        /*if (AccessToken.getCurrentAccessToken() != null) {
            Intent intent = new Intent(MainActivity.this, HomeActivity.class);
            startActivity(intent);
            MainActivity.this.finish();
        }*/
    }
}
