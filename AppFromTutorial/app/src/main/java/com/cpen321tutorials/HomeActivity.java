package com.cpen321tutorials;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.Profile;

import org.json.JSONException;
import org.json.JSONObject;

public class HomeActivity extends AppCompatActivity {
    private final String TAG = "HomeActivity";

    AccessTokenTracker accessTokenTracker;
    AccessToken accessToken;

    TextView emailInfoTextView;

    Button back_btn;
    Button picture_btn;

    private int CAMERA_PERMISSION_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        emailInfoTextView = (TextView) findViewById(R.id.email_info);
        back_btn = (Button) findViewById(R.id.btn_back);
        back_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                startActivity(intent);

            }
        });

        picture_btn = (Button) findViewById(R.id.btn_take_pic);
        picture_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkCameraPermissions()) {
                    new AlertDialog.Builder(HomeActivity.this)
                            .setTitle("Camera Requested Accepted")
                            .setMessage("This app now has access to Camera to detect when the user dabs")
                            .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                }
                            })
                            .create()
                            .show();
                }
            }
        });

        fillUserInfo();
        trackFBAccessToken();
    }

    private boolean checkCameraPermissions() {
        boolean result = false;
        Log.d(TAG, "Requesting Permissions");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            result = true;
        }
        else {
            requestCameraPermission();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                result = true;
            }
        }
        return result;
    }

    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new AlertDialog.Builder(HomeActivity.this)
                    .setTitle("Camera Permission Needed")
                    .setMessage("This app requires Camera permissions to detect when the user dabs")
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(HomeActivity.this, new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            Toast.makeText(HomeActivity.this, "The camera permission is required for detecting dabs.", Toast.LENGTH_SHORT);
                        }
                    })
                    .create()
                    .show();
        }
        else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void trackFBAccessToken() {
        accessTokenTracker = new AccessTokenTracker() {
            @Override
            protected void onCurrentAccessTokenChanged(AccessToken oldAccessToken, AccessToken currentAccessToken) {
                if (currentAccessToken == null) {
                    Log.d(TAG, "Creating intent to go back to MainActivity");
                    Intent intent = new Intent(HomeActivity.this, MainActivity.class);
                    startActivity(intent);
                }
            }
        };
        accessTokenTracker.startTracking();
    }


    private void fillUserInfo() {
        accessToken = AccessToken.getCurrentAccessToken();
        if (accessToken == null) {
            Log.d(TAG, "Something went wrong, please re-login and try again");
        }
        else {
            GraphRequest graphRequest = GraphRequest.newMeRequest(accessToken, new GraphRequest.GraphJSONObjectCallback() {
                @Override
                public void onCompleted(JSONObject object, GraphResponse response) {
                    displayUserInfo(object);
                }
            });

            Bundle parameters = new Bundle();
            parameters.putString("fields", "email");
            graphRequest.setParameters(parameters);
            graphRequest.executeAsync();
        }
    }

    private void displayUserInfo(JSONObject object) {
        String email = "";
        try {
            email = object.getString("email");
            emailInfoTextView.setText(email);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }





}
