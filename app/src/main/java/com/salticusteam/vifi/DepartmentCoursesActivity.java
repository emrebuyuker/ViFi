package com.salticusteam.vifi;

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

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class DepartmentCoursesActivity extends AppCompatActivity {


    ListView listView;
    TextView textViewTitle;

    String lessonName;

    FirebaseDatabase firebaseDatabase;

    ArrayList<String> lessonNamesFB;

    Context context = this;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_department_courses);

        MobileAds.initialize(this,
                "ca-app-pub-9037305793844471~3834061477");

        AdView mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        listView = (ListView) findViewById(R.id.listView);
        textViewTitle = (TextView) findViewById(R.id.textViewTitle);

        lessonNamesFB = new ArrayList<String>();

        firebaseDatabase = FirebaseDatabase.getInstance();

        getDataFirebaseMain2Activity();
        listViewOnClick();

    }

    private void getDataFirebaseMain2Activity() {

        Intent intent = getIntent();

        final String uniName = intent.getStringExtra("uniName");
        final String fakName = intent.getStringExtra("fakName");
        final String bolName = intent.getStringExtra("bolName");

        textViewTitle.setText(bolName);

        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniName).child(fakName).child(bolName);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    System.out.println("dataSnapshot= " + dataSnapshot);

                    if (!ds.getKey().equals("bolname")) {

                        HashMap<String, Object> facultyMap = (HashMap<String, Object>) ds.getValue();
                        lessonNamesFB.add((String) facultyMap.get("lessonname"));

                    }
                }
                ArrayAdapter<String> veriAdaptoru = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, android.R.id.text1, lessonNamesFB);
                listView.setAdapter(veriAdaptoru);
                System.out.println("lessonNamesFB= " + lessonNamesFB);

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

                lessonName = lessonNamesFB.get(position);
                System.out.println("parent= " + lessonName);

                Intent intent = getIntent();

                final String uniName = intent.getStringExtra("uniName");
                final String fakName = intent.getStringExtra("fakName");
                final String bolName = intent.getStringExtra("bolName");

                Intent intent2 = new Intent(getApplicationContext(), CourseExamListActivity.class);

                intent2.putExtra("uniName", uniName);
                intent2.putExtra("fakName", fakName);
                intent2.putExtra("bolName", bolName);
                intent2.putExtra("lesson", lessonName);

                startActivity(intent2);

            }
        });
    };

}
