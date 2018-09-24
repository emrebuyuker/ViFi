package com.salticusteam.vifi;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ListView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class Main3Activity extends AppCompatActivity {


    ArrayList<String> imageNamesFB;
    ArrayList<String> imagesFB;

    FirebaseDatabase firebaseDatabase;
    DatabaseReference myRef;
    ListView listView;

    PostClass adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);

        /*Intent intent = getIntent();

        final String uniName = intent.getStringExtra("uniName");
        final String fakName = intent.getStringExtra("fakName");
        final String bolName = intent.getStringExtra("bolName");
        final String lessonName = intent.getStringExtra("lessonName");

        startActivity(intent);*/


        imageNamesFB = new ArrayList<String>();
        imagesFB = new ArrayList<String>();

        firebaseDatabase = FirebaseDatabase.getInstance();

        listView = findViewById(R.id.listView);

        listView.setAdapter(adapter);


        //TextView textView = (TextView) findViewById(R.id.textView4);
        //Intent intent = getIntent();
        //final String lessonName = intent.getStringExtra("lessonName");
        //textView.setText(lessonName);


    }
}
