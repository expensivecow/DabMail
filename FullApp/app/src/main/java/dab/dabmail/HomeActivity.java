package dab.dabmail;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.Profile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.InputStream;

import de.hdodenhof.circleimageview.CircleImageView;

public class HomeActivity extends AppCompatActivity {
    private final String TAG = "HomeActivity";
    AccessTokenTracker accessTokenTracker;
    AccessToken accessToken;
    Button sendEmailButton;
    EditText fromEmailET;
    EditText toEmailET;
    EditText emailBodyET;
    CircleImageView displayPic;
    TextView welcomeTextView;

    private int GRANT_PERMISSION_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideTitleBar();
        setContentView(R.layout.activity_home);
        fromEmailET = (EditText) findViewById(R.id.from_email);
        toEmailET = (EditText) findViewById(R.id.to_email);
        emailBodyET = (EditText) findViewById(R.id.body);
        welcomeTextView = (TextView) findViewById(R.id.welcome_tv);
        displayPic = (CircleImageView) findViewById(R.id.profile_image);

        sendEmailButton = (Button) findViewById(R.id.send_email_button);
        sendEmailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (inputDataIsValid()) {
                    if (checkRequiredPermissions()) {
                        Intent intent = new Intent(HomeActivity.this, CameraActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putString("from_email", fromEmailET.getText().toString());
                        bundle.putString("to_email", toEmailET.getText().toString());
                        bundle.putString("body_email", emailBodyET.getText().toString());
                        intent.putExtras(bundle);
                        startActivity(intent);
                    }
                }
                else {
                    Toast.makeText(HomeActivity.this, getString(R.string.valid_email_input_msg), Toast.LENGTH_LONG).show();
                }
            }
        });

        fillUserInfo();
        trackFBAccessToken();
    }

    // Request Camera (For detecting dabs) and Read/Write External Storage (For saving images) Permissions
    private boolean checkRequiredPermissions() {
        boolean result = false;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            result = true;
        }
        else {
            requestRequiredPermissions();

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                result = true;
            }
        }

        return result;
    }

    private void requestRequiredPermissions() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new AlertDialog.Builder(HomeActivity.this)
                    .setTitle("Camera, Read/Write External Storage Permissions Needed")
                    .setMessage("This app requires Camera permissions to detect when the user dabs, and Read/Write external storage permissions to attach the dabbing image to email.")
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(HomeActivity.this, new String[] {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, GRANT_PERMISSION_CODE);
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            Toast.makeText(HomeActivity.this, "We require all permissions to be granted before we can dab on your friends.", Toast.LENGTH_SHORT);
                        }
                    })
                    .create()
                    .show();
        }
        else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, GRANT_PERMISSION_CODE);
        }
    }

    private boolean inputDataIsValid() {
        return isValidEmail(fromEmailET.getText().toString()) && isValidEmail(toEmailET.getText().toString());
    }

    private boolean isValidEmail(String e) {
        String regex = "^[\\w-_\\.+]*[\\w-_\\.]\\@([\\w]+\\.)+[\\w]+[\\w]$";
        return e.matches(regex);
    }


    private void trackFBAccessToken() {
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
            GraphRequest graphRequest = GraphRequest.newMeRequest(accessToken, new GraphRequest.GraphJSONObjectCallback() {
                @Override
                public void onCompleted(JSONObject object, GraphResponse response) {
                    displayUserInfo(object);
                }
            });

            Bundle parameters = new Bundle();
            parameters.putString("fields", "first_name, email, id");
            graphRequest.setParameters(parameters);
            graphRequest.executeAsync();

            Profile profile = Profile.getCurrentProfile();
            if (profile != null) {
                Uri profilePicURI = profile.getProfilePictureUri(196, 196);
                ContentResolver res = this.getContentResolver();
                InputStream in = null;
                try {
                    in = res.openInputStream(profilePicURI);
                    Bitmap dp = BitmapFactory.decodeStream(in);
                    displayPic.setImageBitmap(dp);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void displayUserInfo(JSONObject object) {
        String first_name = "";
        String email = "";
        String id = "";
        try {
            first_name = object.getString("first_name");
            Log.d(TAG, first_name);
            email = object.getString("email");
            id = object.getString("id");

            welcomeTextView.setText("Welcome Back, " + first_name + "!");
            fromEmailET.setText(email);
            toEmailET.setText("michael.cao@alumni.ubc.ca");

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void hideTitleBar() {
        // Remove status bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Remove title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);//will hide the title
        getSupportActionBar().hide(); //hide the title bar
    }
}
