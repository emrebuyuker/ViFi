package com.salticusteam.vifi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class ExamDetailActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {


    ArrayList<String> imageNamesFB;
    ArrayList<String> imagesFB;

    FirebaseDatabase firebaseDatabase;
    DatabaseReference myRef;
    ListView listView;

    TextView textViewTitle;

    String seleckImageURL;

    PostClass adapter;

    Context context = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_detail);


        imageNamesFB = new ArrayList<String>();
        imagesFB = new ArrayList<String>();

        listView = findViewById(R.id.listView);
        textViewTitle = (TextView) findViewById(R.id.textViewTitle);

        firebaseDatabase = FirebaseDatabase.getInstance();

        myRef = firebaseDatabase.getReference();

        listView.setOnItemSelectedListener(this);

        adapter = new PostClass(imagesFB, this);

        listView.setAdapter(adapter);


        getDataFirebase();
        listViewOnClick();


        //TextView textView = (TextView) findViewById(R.id.textView4);
        //Intent intent = getIntent();
        //final String lessonName = intent.getStringExtra("lessonName");
        //textView.setText(lessonName);

    }

    private void getDataFirebase() {

        Intent intent = getIntent();

        final String uniName = intent.getStringExtra("uniName");
        final String fakName = intent.getStringExtra("fakName");
        final String bolName = intent.getStringExtra("bolName");
        final String lessonName = intent.getStringExtra("lesson");
        final String imagesName = intent.getStringExtra("imagename");

        textViewTitle.setText(imagesName);

        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniName).child(fakName).child(bolName).child(lessonName).child(imagesName);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    System.out.println("dataSnapshot= "+dataSnapshot);

                    if (!ds.getKey().equals("imagename")){

                        HashMap<String, Object> imageNameMap = (HashMap<String, Object>) ds.getValue();
                        imagesFB.add((String) imageNameMap.get("downloadURL"));

                    }
                }
                ArrayAdapter<String> veriAdaptoru=new ArrayAdapter<String> (context, android.R.layout.simple_list_item_1, android.R.id.text1,imageNamesFB);
                listView.setAdapter(veriAdaptoru);
                System.out.println("images= "+imagesFB);

                adapter = new PostClass(imagesFB, (Activity) context);

                listView.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void listViewOnClick() {

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                seleckImageURL = imagesFB.get(position);
                System.out.println("seleckImageURL= "+seleckImageURL);

                Intent intent = new Intent(getApplicationContext(),ImageZoomActivity.class);

                intent.putExtra("seleckImageURL",seleckImageURL);

                startActivity(intent);

            }
        });



        /*listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                seleckImageName = imageNamesFB.get(position);
                System.out.println("seleckImageName= "+seleckImageName);

                Intent intent = getIntent();

                final String uniName = intent.getStringExtra("uniName");
                final String fakName = intent.getStringExtra("fakName");
                final String bolName = intent.getStringExtra("bolName");
                final String lessonName = intent.getStringExtra("lesson");

                DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniName).child(fakName).child(bolName).child(lessonName).child(seleckImageName);
                newReference.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                        for (DataSnapshot ds : dataSnapshot.getChildren()) {

                            System.out.println("dataSnapshot= "+dataSnapshot);

                            if (!ds.getKey().equals("lessonname")){

                                HashMap<String, Object> imageNameMap = (HashMap<String, Object>) ds.getValue();
                                imageNamesFB.add((String) imageNameMap.get("imagename"));
                                imagesFB.add((String) imageNameMap.get("downloadURL"));

                            }
                        }
                        ArrayAdapter<String> veriAdaptoru=new ArrayAdapter<String> (context, android.R.layout.simple_list_item_1, android.R.id.text1,imageNamesFB);
                        listView.setAdapter(veriAdaptoru);
                        System.out.println("lessonNamesFB3= "+imageNamesFB);

                        adapter = new PostClass(imageNamesFB,imagesFB, (Activity) context);

                        listView.setAdapter(adapter);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });


            }
        });*/

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        final Intent intent = getIntent();

        final String uniName = intent.getStringExtra("uniName");
        final String fakName = intent.getStringExtra("fakName");
        final String bolName = intent.getStringExtra("bolName");
        final String lessonName = intent.getStringExtra("lesson");

        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniName).child(fakName).child(bolName).child(lessonName);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    System.out.println("dataSnapshot= "+dataSnapshot);

                    if (!ds.getKey().equals("lessonname")){

                        HashMap<String, Object> imageNameMap = (HashMap<String, Object>) ds.getValue();
                        imageNamesFB.add((String) imageNameMap.get("imagename"));
                        imagesFB.add((String) imageNameMap.get("downloadURL"));

                    }
                }
                ArrayAdapter<String> veriAdaptoru=new ArrayAdapter<String> (context, android.R.layout.simple_list_item_1, android.R.id.text1,imageNamesFB);
                listView.setAdapter(veriAdaptoru);
                System.out.println("lessonNamesFB= "+imageNamesFB);



               // Intent intent2 = new Intent(getApplicationContext(),ExamDetailActivity.class);
               // startActivity(intent2);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

}