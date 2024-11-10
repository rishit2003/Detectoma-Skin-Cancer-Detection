package com.example.detectoma;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.appcompat.app.AppCompatActivity;

public class ScreeningActivity extends AppCompatActivity implements UserDataDialogFragment.UserDataListener {

    private Button userDataButton, takePhotoButton, takeTempButton, takeDistButton, analyzeButton;
    private CheckBox userDataCheckBox, takePhotoCheckBox, takeTempCheckBox, takeDistCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screening);

        // Initialize buttons
        userDataButton = findViewById(R.id.userDataButton);
        takePhotoButton = findViewById(R.id.takePhotoButton);
        takeTempButton = findViewById(R.id.takeTempButton);
        takeDistButton = findViewById(R.id.takeDistButton);
        analyzeButton = findViewById(R.id.analyzeButton);

        // Initialize checkboxes
        userDataCheckBox = findViewById(R.id.userDataCheckBox);
        takePhotoCheckBox = findViewById(R.id.takePhotoCheckBox);
        takeTempCheckBox = findViewById(R.id.takeTempCheckBox);
        takeDistCheckBox = findViewById(R.id.takeDistCheckBox);

        // Set click listeners for buttons
        userDataButton.setOnClickListener(v -> openUserDataDialog());
        takePhotoButton.setOnClickListener(v -> openTakePhotoActivity());
        takeTempButton.setOnClickListener(v -> openTakeTempDialog());
        takeDistButton.setOnClickListener(v -> openTakeDietDialog());

        analyzeButton.setOnClickListener(v -> openResultsActivity());

        // Update the initial button states
        updateButtonState();
    }

    private void updateButtonState() {
        // Enable the next button only if the previous task is complete
        takePhotoButton.setEnabled(userDataCheckBox.isChecked());
        takeTempButton.setEnabled(takePhotoCheckBox.isChecked());
        takeDistButton.setEnabled(takeTempCheckBox.isChecked());
        analyzeButton.setEnabled(userDataCheckBox.isChecked() && takePhotoCheckBox.isChecked() &&
                takeTempCheckBox.isChecked() && takeDistCheckBox.isChecked());
    }

    private void openUserDataDialog() {
        // Show the dialog to collect user data
        UserDataDialogFragment dialog = new UserDataDialogFragment();
        dialog.show(getSupportFragmentManager(), "UserDataDialog");
    }

    @Override
    public void onUserDataCompleted() {
        // Mark the checkbox as completed and update the button state
        userDataCheckBox.setChecked(true);
        updateButtonState();
    }

    private void openTakePhotoActivity() {
        // Simulate completion of photo-taking activity
        takePhotoCheckBox.setChecked(true);
        updateButtonState();
    }

    private void openTakeTempDialog() {
        // Simulate completion of temperature-taking activity
        takeTempCheckBox.setChecked(true);
        updateButtonState();
    }

    private void openTakeDietDialog() {
        // Simulate completion of diet-taking activity
        takeDistCheckBox.setChecked(true);
        updateButtonState();
    }

    private void openResultsActivity() {
        // Open ResultsActivity once all items are completed
        Intent intent = new Intent(ScreeningActivity.this, resultsActivity.class);
        startActivity(intent);
    }
}
