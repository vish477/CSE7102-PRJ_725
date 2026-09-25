package com.example.geosafe;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ContactManager {

    private static final String PREF_NAME = "GeoSafeContacts";
    private static final String KEY_CONTACTS = "contacts";
    private static final int MAX_CONTACTS = 5;

    private final SharedPreferences preferences;

    public ContactManager(Context context) {
        preferences = context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );
    }

    public boolean addContact(String name, String phone) {

        name = name.trim();
        phone = phone.trim();

        if (name.isEmpty() || phone.isEmpty()) {
            return false;
        }

        // Remove spaces, hyphens and brackets
        phone = phone
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");

        // Convert Indian 10-digit number to +91
        if (phone.matches("\\d{10}")) {
            phone = "+91" + phone;
        }

        List<Contact> contacts = getContacts();

        if (contacts.size() >= MAX_CONTACTS) {
            return false;
        }

        contacts.add(new Contact(name, phone));

        saveContacts(contacts);

        return true;
    }

    public boolean deleteContact(int position) {

        List<Contact> contacts = getContacts();

        if (position < 0 || position >= contacts.size()) {
            return false;
        }

        contacts.remove(position);

        saveContacts(contacts);

        return true;
    }

    public List<Contact> getContacts() {

        List<Contact> contacts = new ArrayList<>();

        String json = preferences.getString(
                KEY_CONTACTS,
                "[]"
        );

        try {

            JSONArray array = new JSONArray(json);

            for (int i = 0; i < array.length(); i++) {

                JSONObject object = array.getJSONObject(i);

                String name = object.optString(
                        "name",
                        ""
                );

                String phone = object.optString(
                        "phone",
                        ""
                );

                if (!name.isEmpty() && !phone.isEmpty()) {

                    contacts.add(
                            new Contact(name, phone)
                    );
                }
            }

        } catch (Exception ignored) {
        }

        return contacts;
    }

    public String[] getPhoneNumbers() {

        List<Contact> contacts = getContacts();

        String[] numbers = new String[contacts.size()];

        for (int i = 0; i < contacts.size(); i++) {

            numbers[i] = contacts
                    .get(i)
                    .getPhone();
        }

        return numbers;
    }

    private void saveContacts(List<Contact> contacts) {

        JSONArray array = new JSONArray();

        try {

            for (Contact contact : contacts) {

                JSONObject object = new JSONObject();

                object.put(
                        "name",
                        contact.getName()
                );

                object.put(
                        "phone",
                        contact.getPhone()
                );

                array.put(object);
            }

        } catch (Exception ignored) {
        }

        preferences
                .edit()
                .putString(
                        KEY_CONTACTS,
                        array.toString()
                )
                .apply();
    }

    public static class Contact {

        private final String name;
        private final String phone;

        public Contact(
                String name,
                String phone
        ) {
            this.name = name;
            this.phone = phone;
        }

        public String getName() {
            return name;
        }

        public String getPhone() {
            return phone;
        }
    }
}