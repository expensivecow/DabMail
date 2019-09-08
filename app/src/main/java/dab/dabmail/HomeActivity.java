package dab.dabmail;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;

public class HomeActivity extends AppCompatActivity {
    private final String TAG = "HomeActivity";
    AccessTokenTracker accessTokenTracker;
    AccessToken accessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideTitleBar();
        setContentView(R.layout.activity_home);

        accessTokenTracker = new AccessTokenTracker() {
            @Override
            protected void onCurrentAccessTokenChanged(AccessToken oldAccessToken, AccessToken currentAccessToken) {
                if (currentAccessToken == null) {
                    Log.d(TAG, "Creating intent to go back to LoginActivity");
                    Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
                    startActivity(intent);
                }
            }
        };
        accessTokenTracker.startTracking();


    }

    private void fillUserInfo() {
        accessToken = AccessToken.getCurrentAccessToken();
        if (accessToken == null) {
            Toast.makeText(this, R.string.relogin_error_msg, Toast.LENGTH_LONG);
        }
        else {

        }
    }

    private void clearUserInfo() {

    }

    private void hideTitleBar() {
        // Remove status bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Remove title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);//will hide the title
        getSupportActionBar().hide(); //hide the title bar
    }
}
