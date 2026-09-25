package com.example.geosafe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class TrustedContactsActivity extends AppCompatActivity {

    private EditText contactNameEditText;
    private EditText contactNumberEditText;
    private LinearLayout contactsContainer;

    private ContactManager contactManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_trusted_contacts);

        contactManager = new ContactManager(this);

        contactNameEditText =
                findViewById(R.id.contactNameEditText);

        contactNumberEditText =
                findViewById(R.id.contactNumberEditText);

        contactsContainer =
                findViewById(R.id.contactsContainer);

        Button addContactButton =
                findViewById(R.id.addContactButton);

        // CONTINUE button
        Button continueButton =
                findViewById(R.id.continueButton);

        // Add trusted contact
        addContactButton.setOnClickListener(
                view -> addContact()
        );

        // Continue to BPM + GPS Monitoring
        continueButton.setOnClickListener(
                view -> continueToMonitoring()
        );

        displayContacts();
    }

    // =========================================================
    // ADD TRUSTED CONTACT
    // =========================================================

    private void addContact() {

        String name =
                contactNameEditText
                        .getText()
                        .toString()
                        .trim();

        String phone =
                contactNumberEditText
                        .getText()
                        .toString()
                        .trim();

        if (name.isEmpty()) {

            contactNameEditText.setError(
                    "Enter contact name"
            );

            return;
        }

        if (phone.isEmpty()) {

            contactNumberEditText.setError(
                    "Enter phone number"
            );

            return;
        }

        String cleanedPhone =
                phone.replace(" ", "")
                        .replace("-", "")
                        .replace("(", "")
                        .replace(")", "");

        // Convert Indian 10-digit number to +91
        if (cleanedPhone.matches("\\d{10}")) {

            cleanedPhone =
                    "+91" + cleanedPhone;
        }

        if (cleanedPhone.length() < 10) {

            contactNumberEditText.setError(
                    "Enter a valid phone number"
            );

            return;
        }

        if (contactManager.getContacts().size() >= 5) {

            Toast.makeText(
                    this,
                    "Maximum 5 trusted contacts allowed",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        boolean added =
                contactManager.addContact(
                        name,
                        cleanedPhone
                );

        if (added) {

            contactNameEditText.setText("");
            contactNumberEditText.setText("");

            Toast.makeText(
                    this,
                    "Trusted contact added",
                    Toast.LENGTH_SHORT
            ).show();

            displayContacts();

        } else {

            Toast.makeText(
                    this,
                    "Unable to add contact",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // =========================================================
    // CONTINUE TO MONITORING
    // =========================================================

    private void continueToMonitoring() {

        List<ContactManager.Contact> contacts =
                contactManager.getContacts();

        // Do not allow monitoring without a contact
        if (contacts.isEmpty()) {

            Toast.makeText(
                    this,
                    "Please add at least one trusted contact first",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Open BPM + GPS Monitoring
        Intent intent =
                new Intent(
                        TrustedContactsActivity.this,
                        MainActivity.class
                );

        startActivity(intent);

        finish();
    }

    // =========================================================
    // DISPLAY TRUSTED CONTACTS
    // =========================================================

    private void displayContacts() {

        contactsContainer.removeAllViews();

        List<ContactManager.Contact> contacts =
                contactManager.getContacts();

        if (contacts.isEmpty()) {

            TextView emptyText =
                    new TextView(this);

            emptyText.setText(
                    "No trusted contacts added yet."
            );

            emptyText.setTextSize(16);
            emptyText.setTextColor(0xFFAAAAAA);
            emptyText.setGravity(Gravity.CENTER);

            emptyText.setPadding(
                    20,
                    30,
                    20,
                    30
            );

            contactsContainer.addView(emptyText);

            return;
        }

        for (int i = 0;
             i < contacts.size();
             i++) {

            final int position = i;

            ContactManager.Contact contact =
                    contacts.get(i);

            LinearLayout card =
                    new LinearLayout(this);

            card.setOrientation(
                    LinearLayout.VERTICAL
            );

            card.setPadding(
                    20,
                    20,
                    20,
                    20
            );

            card.setBackgroundColor(
                    0xFF242424
            );

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            cardParams.setMargins(
                    0,
                    0,
                    0,
                    15
            );

            card.setLayoutParams(cardParams);

            TextView nameText =
                    new TextView(this);

            nameText.setText(
                    "👤 " + contact.getName()
            );

            nameText.setTextSize(19);
            nameText.setTextColor(0xFFFFFFFF);

            TextView phoneText =
                    new TextView(this);

            phoneText.setText(
                    "📱 " + contact.getPhone()
            );

            phoneText.setTextSize(16);
            phoneText.setTextColor(0xFFBBBBBB);

            Button deleteButton =
                    new Button(this);

            deleteButton.setText(
                    "DELETE"
            );

            deleteButton.setOnClickListener(
                    view -> {

                        contactManager.deleteContact(
                                position
                        );

                        displayContacts();

                        Toast.makeText(
                                this,
                                "Contact deleted",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );

            card.addView(nameText);
            card.addView(phoneText);
            card.addView(deleteButton);

            contactsContainer.addView(card);
        }
    }
}