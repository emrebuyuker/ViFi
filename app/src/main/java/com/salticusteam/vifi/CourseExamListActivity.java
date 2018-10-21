package com.salticusteam.vifi;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class CourseExamListActivity extends AppCompatActivity {

    ListView listView ;
    TextView textViewTitle;

    String imagesName;

    FirebaseDatabase firebaseDatabase;

    ArrayList<String> imageNamesFB;
    ArrayList<String> imagesFB;

    Context context = this;

    Intent intent2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_exam_list);

        AdView mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        firebaseDatabase = FirebaseDatabase.getInstance();

        imageNamesFB = new ArrayList<String>();
        imagesFB = new ArrayList<String>();

        listView = (ListView) findViewById(R.id.listView);
        textViewTitle = (TextView) findViewById(R.id.textViewTitle);

        //listView.setOnItemSelectedListener(this);

        getDataFirebaseMain6Activity();
        listViewOnClick();

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return super.onContextItemSelected(item);
    }

    private void getDataFirebaseMain6Activity() {

        Intent intent = getIntent();

        final String uniName = intent.getStringExtra("uniName");
        final String fakName = intent.getStringExtra("fakName");
        final String bolName = intent.getStringExtra("bolName");
        final String lessonName = intent.getStringExtra("lesson");

        textViewTitle.setText(lessonName);

        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniName).child(fakName).child(bolName).child(lessonName);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    System.out.println("dataSnapshot= "+dataSnapshot);

                    if (!ds.getKey().equals("lessonname")){

                        HashMap<String, Object> imageNameMap = (HashMap<String, Object>) ds.getValue();
                        imageNamesFB.add((String) imageNameMap.get("imagename"));

                    }
                }
                ArrayAdapter<String> veriAdaptoru=new ArrayAdapter<String> (context, android.R.layout.simple_list_item_1, android.R.id.text1,imageNamesFB);
                listView.setAdapter(veriAdaptoru);
                System.out.println("lessonNamesFB6= "+imageNamesFB);

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


                imagesName = imageNamesFB.get(position);
                System.out.println("imageName= "+imagesName);

                Intent intent = getIntent();

                final String uniName = intent.getStringExtra("uniName");
                final String fakName = intent.getStringExtra("fakName");
                final String bolName = intent.getStringExtra("bolName");
                final String lessonName = intent.getStringExtra("lesson");

                if (imagesName.equals("2014 FINAL")){
                    intent2 = new Intent(getApplicationContext(),ExamDetailPdfActivity.class);
                }else{
                    intent2 = new Intent(getApplicationContext(),ExamDetailActivity.class);
                }

                intent2.putExtra("uniName",uniName);
                intent2.putExtra("fakName",fakName);
                intent2.putExtra("bolName",bolName);
                intent2.putExtra("lesson",lessonName);
                intent2.putExtra("imagename",imagesName);

                startActivity(intent2);

            }
        });
    };
}
