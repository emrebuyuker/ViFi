package com.salticusteam.vifi;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.github.barteksc.pdfviewer.PDFView;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

public class ExamDetailPdfActivity extends AppCompatActivity {

    String selectPdfName;
    ArrayList<String> pdfNamesFB;

    TextView textViewTitle;

    PDFView pdfView;

    FirebaseDatabase firebaseDatabase;
    DatabaseReference myRef;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exam_detail_pdf);

        MobileAds.initialize(this,
                "ca-app-pub-9037305793844471~3834061477");

        AdView mAdView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);


        firebaseDatabase = FirebaseDatabase.getInstance();

        pdfNamesFB = new ArrayList<String>();

        pdfView = (PDFView) findViewById(R.id.pdfView);

        textViewTitle = (TextView) findViewById(R.id.textViewTitle);

        getDataFirebase();


    }
    private void getDataFirebase() {

        Intent intent = getIntent();

        final String uniName = intent.getStringExtra("uniName");
        final String fakName = intent.getStringExtra("fakName");
        final String bolName = intent.getStringExtra("bolName");
        final String lessonName = intent.getStringExtra("lesson");
        final String imagesName = intent.getStringExtra("imagename");
        final String type = intent.getStringExtra("type");

        textViewTitle.setText(imagesName);


        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniName).child(fakName).child(bolName).child(lessonName).child(imagesName).child(type);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    System.out.println("dataSnapshot= "+dataSnapshot);

                    if (!ds.getKey().equals("type")){

                        HashMap<String, Object> imageNameMap = (HashMap<String, Object>) ds.getValue();
                        pdfNamesFB.add((String) imageNameMap.get("downloadURL"));

                    }
                }

                selectPdfName = pdfNamesFB.get(0);

                new RetrievePDFStream().execute(selectPdfName );


            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }


        });

    }
    class RetrievePDFStream extends AsyncTask<String,Void,InputStream> {

        @Override
        protected InputStream doInBackground(String... strings) {
            InputStream inpuInputStream = null;
            try {
                URL url = new URL(strings[0]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                if (urlConnection.getResponseCode() == 200 )
                {
                    inpuInputStream = new BufferedInputStream(urlConnection.getInputStream());
                }
            }
            catch (IOException e){
                return null;
            }
            return inpuInputStream;
        }

        @Override
        protected void onPostExecute(InputStream inputStream) {
            pdfView.fromStream(inputStream).load();
        }
    }
}
