package com.salticusteam.vifi;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.widget.ListView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class Main3Activity extends AppCompatActivity {

    ArrayList<String> uniNamesFB;
    ArrayList<String> fakNamesFB;
    ArrayList<String> bolNamesFB;
    ArrayList<String> lessonNamesFB;
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

        uniNamesFB = new ArrayList<String>();
        fakNamesFB = new ArrayList<String>();
        bolNamesFB = new ArrayList<String>();
        lessonNamesFB = new ArrayList<String>();
        imageNamesFB = new ArrayList<String>();
        imagesFB = new ArrayList<String>();

        firebaseDatabase = FirebaseDatabase.getInstance();
        myRef = firebaseDatabase.getReference();

        listView = findViewById(R.id.listView);

        adapter = new PostClass(uniNamesFB,fakNamesFB,bolNamesFB,lessonNamesFB,imageNamesFB,imagesFB,this);

        listView.setAdapter(adapter);

        getDataFromFireBase();


        //TextView textView = (TextView) findViewById(R.id.textView4);
        //Intent intent = getIntent();
        //final String lessonName = intent.getStringExtra("lessonName");
        //textView.setText(lessonName);


    }

    protected  void getDataFromFireBase() {

        DatabaseReference newReference = firebaseDatabase.getReference("Universities");
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    HashMap<String, Object> hashMap = (HashMap<String, Object>) ds.getValue();

                    uniNamesFB.add((String) hashMap.get("uniname"));

                    for (String key: hashMap.keySet()) {
                        if (!key.equals("uniname")) {
                            HashMap<String, Object>  facultyMap = (HashMap<String, Object>) hashMap.get(key);
                            fakNamesFB.add((String) facultyMap.get("fakname"));

                            for (String facultyKey: facultyMap.keySet()) {
                                if (!facultyKey.equals("fakname")) {
                                    HashMap<String, Object>  departmentMap = (HashMap<String, Object>) facultyMap.get(facultyKey);
                                    bolNamesFB.add((String) departmentMap.get("bolname"));

                                    for (String departmanKey: departmentMap.keySet()){
                                        if (!departmanKey.equals("bolname")) {
                                            HashMap<String, Object>  lessonMap = (HashMap<String, Object>) departmentMap.get(departmanKey);
                                            lessonNamesFB.add((String) lessonMap.get("lessonname"));

                                            for (String lessonKey:lessonMap.keySet()) {
                                                if (!lessonKey.equals("lessonname")){
                                                    HashMap<String, Object> imageNameMap = (HashMap<String, Object>) lessonMap.get(lessonKey);
                                                    imageNamesFB.add((String) imageNameMap.get("imagename"));
                                                    imagesFB.add((String) imageNameMap.get("downloadURL"));
                                                }
                                            }
                                        }


                                    }
                                }
                            }
                        }
                    }
/*
                    fakNamesFB.add(hashMap.get("fakname"));
                    bolNamesFB.add(hashMap.get("bolname"));
                    lessonNamesFB.add(hashMap.get("lessonname"));
                    imageNamesFB.add(hashMap.get("imagename"));
                    imagesFB.add(hashMap.get("downloadURL"));*/

                    adapter.notifyDataSetChanged();

                }
                System.out.println("uniname "+uniNamesFB);
                System.out.println("fakname "+fakNamesFB);
                System.out.println("bolname "+bolNamesFB);
                System.out.println("lessoname "+lessonNamesFB);
                System.out.println("imagename "+imageNamesFB);
                System.out.println("image "+imagesFB);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }
}
