package com.redeluxe;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity implements SensorEventListener {
    private EditText emailInput;
    private EditText passwordInput;
    private EditText usernameInput;
    private TextInputLayout usernameInputLayout;
    private Button loginButton;
    private Button registerButton;
    private TextView switchModeText;
    private boolean isRegisterMode = false;
    private ApiService apiService;
    
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private DiamondRainView diamondRainView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        initSensors();
        apiService = new ApiService(this);
    }

    private void initViews() {
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        usernameInput = findViewById(R.id.usernameInput);
        usernameInputLayout = findViewById(R.id.usernameInputLayout);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        switchModeText = findViewById(R.id.switchModeText);
        diamondRainView = findViewById(R.id.diamondRainView);

        loginButton.setOnClickListener(v -> login());
        registerButton.setOnClickListener(v -> register());
        switchModeText.setOnClickListener(v -> switchMode());

        updateUI();
    }
    
    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && diamondRainView != null) {
            float x = event.values[0];
            diamondRainView.updateParallax(x * 10); // усиливаем эффект
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // не нужно
    }

    private void switchMode() {
        isRegisterMode = !isRegisterMode;
        updateUI();
    }

    private void updateUI() {
        if (isRegisterMode) {
            usernameInputLayout.setVisibility(android.view.View.VISIBLE);
            loginButton.setVisibility(android.view.View.GONE);
            registerButton.setVisibility(android.view.View.VISIBLE);
            switchModeText.setText("уже есть аккаунт?");
        } else {
            usernameInputLayout.setVisibility(android.view.View.GONE);
            loginButton.setVisibility(android.view.View.VISIBLE);
            registerButton.setVisibility(android.view.View.GONE);
            switchModeText.setText("нет аккаунта?");
        }
    }

    private void login() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "заполните все поля", Toast.LENGTH_SHORT).show();
            return;
        }

        apiService.login(email, password, new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String token) {
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "вход выполнен", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "ошибка входа: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void register() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String username = usernameInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "заполните все поля", Toast.LENGTH_SHORT).show();
            return;
        }

        apiService.register(email, password, username, new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String token) {
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "регистрация выполнена", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "ошибка регистрации: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
} 