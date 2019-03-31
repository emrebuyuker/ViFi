package com.salticusteam.vifi;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.UUID;

public class AddAnnouncements extends AppCompatActivity {

    EditText aTTitle;
    EditText aTBookName;
    EditText aTExplanation;
    EditText aTBookPrice;
    EditText aTContact;

    FirebaseDatabase database;
    DatabaseReference myRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_announcements);

        aTTitle = findViewById(R.id.addEditTextTitle);
        aTBookName = findViewById(R.id.addEditTextBookName);
        aTExplanation = findViewById(R.id.addEditTextExplanation);
        aTBookPrice = findViewById(R.id.addEditTextBookPrice);
        aTContact = findViewById(R.id.addEditTextContact);

        database = FirebaseDatabase.getInstance();
        myRef = database.getReference();

    }

    public void announcementsaddSave(View view) {

        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString();


        myRef.child("Announcements").child(uuidString).child("title").setValue(aTTitle.getText().toString());
        myRef.child("Announcements").child(uuidString).child("bookName").setValue(aTBookName.getText().toString());
        myRef.child("Announcements").child(uuidString).child("acıklama").setValue(aTExplanation.getText().toString());
        myRef.child("Announcements").child(uuidString).child("lessonname").setValue(aTBookPrice.getText().toString());
        myRef.child("Announcements").child(uuidString).child("contact").setValue(aTContact.getText().toString());

        Toast.makeText(getApplicationContext(), "Successful", Toast.LENGTH_LONG).show();

        Intent intent = new Intent(getApplicationContext(), AddAnnouncements.class);
        startActivity(intent);


    }
}
