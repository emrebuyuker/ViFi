package com.salticusteam.vifi;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CourseExamListActivity extends AppCompatActivity {

    ListView listView;
    TextView textViewTitle;

    String imagesName;
    String type;

    FirebaseDatabase firebaseDatabase;

    ArrayList<String> imageNamesFB;
    ArrayList<String> typeFB;
    ArrayList<String> imagesFB;

    Context context = this;

    Integer advertisement = 0;

    Intent intent2;

    private AdView mAdView;

    InterstitialAd mInterstitialAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_exam_list);

        MobileAds.initialize(this, "ca-app-pub-3940256099942544~3347511713");


        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-3940256099942544/8691691433");

        mInterstitialAd.setAdListener(new AdListener() { //reklamımıza listener ekledik ve kapatıldığında haberimiz olacak
            @Override
            public void onAdClosed() { //reklam kapatıldığı zaman tekrardan reklamın yüklenmesi için
                requestNewInterstitial();
            }
        });


        requestNewInterstitial(); //reklamı direk uygulama açıldığında yüklemek için onCreate içinde yapıyoruz reklam yükleme işini

        MobileAds.initialize(this,
                "ca-app-pub-9037305793844471~3834061477");

        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        advertisement = 1;

        firebaseDatabase = FirebaseDatabase.getInstance();

        imageNamesFB = new ArrayList<String>();
        typeFB = new ArrayList<String>();
        imagesFB = new ArrayList<String>();

        listView = (ListView) findViewById(R.id.listView);
        textViewTitle = (TextView) findViewById(R.id.textViewTitle);


        getDataFirebaseMain6Activity();
        listViewOnClick();

    }

    private void requestNewInterstitial() { //Test cihazı ekliyoruz Admob dan ban yememek için
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice("B9C840C4E9AD8EC5D1497C9A62C56374")
                .build();

        mInterstitialAd.loadAd(adRequest);
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

                    System.out.println("dataSnapshot= " + dataSnapshot);

                    if (!ds.getKey().equals("lessonname")) {

                        HashMap<String, Object> imageNameMap = (HashMap<String, Object>) ds.getValue();
                        imageNamesFB.add((String) imageNameMap.get("imagename"));

                    }
                }

                final List<ListViewItemHomeActivity> imageNames = new ArrayList<>();

                for (int i=0 ; i<imageNamesFB.size(); i++){

                    imageNames.add(new ListViewItemHomeActivity(imageNamesFB.get(i)) );

                }

                ListViewAdapter adapter = new ListViewAdapter(CourseExamListActivity.this, imageNames);
                listView.setAdapter(adapter);

                /*ArrayAdapter<String> veriAdaptoru = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, android.R.id.text1, imageNamesFB);
                listView.setAdapter(veriAdaptoru);*/

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void listViewOnClick() {

        System.out.println("emre");


        if (advertisement == 1) {

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                    imagesName = imageNamesFB.get(position);
                    lessonType();

                }
            });
        }
    };

    private void lessonType() {


        Intent intent = getIntent();

        final String uniName = intent.getStringExtra("uniName");
        final String fakName = intent.getStringExtra("fakName");
        final String bolName = intent.getStringExtra("bolName");
        final String lessonName = intent.getStringExtra("lesson");

        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniName).child(fakName).child(bolName).child(lessonName).child(imagesName);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {


                typeFB.clear();

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    System.out.println("dataSnapshot= " + dataSnapshot);

                    if (!ds.getKey().equals("imagename")) {

                        HashMap<String, Object> imageNameMap2 = (HashMap<String, Object>) ds.getValue();
                        typeFB.add((String) imageNameMap2.get("type"));

                    }
                }
                type = typeFB.get(0);

                System.out.println(type);

                if (type.equals("PDF")) {
                    intent2 = new Intent(getApplicationContext(), ExamDetailPdfActivity.class);
                } else {
                    intent2 = new Intent(getApplicationContext(), ExamDetailActivity.class);
                }

                intent2.putExtra("uniName", uniName);
                intent2.putExtra("fakName", fakName);
                intent2.putExtra("bolName", bolName);
                intent2.putExtra("lesson", lessonName);
                intent2.putExtra("imagename", imagesName);
                intent2.putExtra("type", type);

                if (mInterstitialAd.isLoaded()) { //reklam yüklenmişse


                    startActivity(intent2);
                    mInterstitialAd.show(); //reklam gösteriliyor
                }

                else {
                    //Reklam yüklenmediyse yapılacak işlemler

                    startActivity(intent2);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }


}
