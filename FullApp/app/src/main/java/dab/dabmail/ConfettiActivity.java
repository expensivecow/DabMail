package dab.dabmail;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import nl.dionsegijn.konfetti.KonfettiView;
import nl.dionsegijn.konfetti.models.Shape;
import nl.dionsegijn.konfetti.models.Size;

public class ConfettiActivity extends AppCompatActivity {
    TextView confettiText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confetti);

        KonfettiView viewKonfetti = (KonfettiView) findViewById(R.id.viewKonfetti);
        confettiText = (TextView) findViewById(R.id.confetti_comment);

        confettiText.setText("Congrats! You just dabbed on " + getIntent().getStringExtra("to_email"));

        viewKonfetti.build()
                .addColors(Color.YELLOW, Color.GREEN, Color.MAGENTA)
                .setDirection(0.0, 359.0)
                .setSpeed(1f, 5f)
                .setFadeOutEnabled(true)
                .setTimeToLive(2000L)
                .addShapes(Shape.RECT, Shape.CIRCLE)
                .addSizes(new Size(12, 5f))
                .setPosition(-50f, viewKonfetti.getWidth() + 50f, -50f, -50f)
                .streamFor(300, 5000L);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                ConfettiActivity.this.confettiText.setText("");
                finish();
            }
        }, 15000);   //5 seconds
    }
}
