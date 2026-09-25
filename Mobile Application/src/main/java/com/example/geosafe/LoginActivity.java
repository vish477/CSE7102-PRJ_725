package com.example.geosafe;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_login);

        EditText user = findViewById(R.id.etUsername);
        EditText pass = findViewById(R.id.etPassword);
        Button login = findViewById(R.id.btnLogin);

        login.setOnClickListener(v -> {

            String username = user.getText().toString().trim();
            String password = pass.getText().toString();

            if ("admin".equals(username) && "1234".equals(password)) {

                Toast.makeText(
                        LoginActivity.this,
                        "Login successful",
                        Toast.LENGTH_SHORT
                ).show();

                // GO TO TRUSTED CONTACTS FIRST
                Intent intent = new Intent(
                        LoginActivity.this,
                        TrustedContactsActivity.class
                );

                startActivity(intent);
                finish();

            } else {

                Toast.makeText(
                        LoginActivity.this,
                        "Invalid login. Use admin / 1234",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}