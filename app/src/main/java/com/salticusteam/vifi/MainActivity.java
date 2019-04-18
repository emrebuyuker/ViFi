package com.salticusteam.vifi;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    InterstitialAd mInterstitialAd;

    String uniItem;
    String fakItem;
    String bolItem;

    Context context = this;

    FirebaseDatabase firebaseDatabase;

    ArrayList<String> uniNamesFB;
    ArrayList<String> fakNamesFB;
    ArrayList<String> bolNamesFB;


    Spinner spinnerUni;
    Spinner spinnerFak;
    Spinner spinnerBol;


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.add_lesson, menu);

        return super.onCreateOptionsMenu(menu);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.add_lesson) {

            Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
            startActivity(intent);

        }

        return super.onOptionsItemSelected(item);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MobileAds.initialize(this, "ca-app-pub-9037305793844471~3834061477");


        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId("ca-app-pub-9037305793844471/9019406131");

        mInterstitialAd.setAdListener(new AdListener() { //reklamımıza listener ekledik ve kapatıldığında haberimiz olacak
            @Override
            public void onAdClosed() { //reklam kapatıldığı zaman tekrardan reklamın yüklenmesi için
                requestNewInterstitial();
            }
        });


        requestNewInterstitial(); //reklamı direk uygulama açıldığında yüklemek için onCreate içinde yapıyoruz reklam yükleme işini

        Button btnShare = (Button) findViewById(R.id.btnShare);

        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String message = "https://play.google.com/store/apps/details?id=com.salticusteam.vifi";

                shareMyMessage(message);
            }
        });

        firebaseDatabase = FirebaseDatabase.getInstance();

        uniNamesFB = new ArrayList<String>();
        fakNamesFB = new ArrayList<String>();
        bolNamesFB = new ArrayList<String>();


        getDataFirebase();

        spinnerUni = (Spinner) findViewById(R.id.spinnerUni);
        spinnerFak = (Spinner) findViewById(R.id.spinnerFak);
        spinnerBol = (Spinner) findViewById(R.id.spinnerBol);

        spinnerUni.setOnItemSelectedListener(this);
        spinnerFak.setOnItemSelectedListener(this);
        spinnerBol.setOnItemSelectedListener(this);

    }

    private void requestNewInterstitial() { //Test cihazı ekliyoruz Admob dan ban yememek için
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice("B9C840C4E9AD8EC5D1497C9A62C56374")
                .build();

        mInterstitialAd.loadAd(adRequest);
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {


        uniItem = spinnerUni.getSelectedItem().toString();
        System.out.println("unitem= " + uniItem);

        if (adapterView.getId() == R.id.spinnerUni) {

            getDataSpinnerFak();
            fakNamesFB.clear();

        } else if (adapterView.getId() == R.id.spinnerFak) {

            fakItem = spinnerFak.getSelectedItem().toString();
            getDataSpinnerBol();
            bolNamesFB.clear();

        } else if (adapterView.getId() == R.id.spinnerBol) {

            bolItem = spinnerBol.getSelectedItem().toString();
            System.out.println("bolıtem= " + bolItem);

        }
    }

    protected void shareMyMessage(String _message) {

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, _message);
        startActivity(Intent.createChooser(share, "Uygulama linkini paylaş !! "));
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    public void onLoginButtonClick(View view) {

        if (uniItem == null) {

            Toast.makeText(context, "Lütfen internetinizi açınız.", Toast.LENGTH_LONG).show();

        } else {

            if (mInterstitialAd.isLoaded()) { //reklam yüklenmişse


                Intent intent = new Intent(getApplicationContext(), DepartmentCoursesActivity.class);

                intent.putExtra("bolName", bolItem);
                intent.putExtra("fakName", fakItem);
                intent.putExtra("uniName", uniItem);

                startActivity(intent);

                mInterstitialAd.show(); //reklam gösteriliyor


            } else {
                //Reklam yüklenmediyse yapılacak işlemler

                Intent intent = new Intent(getApplicationContext(), DepartmentCoursesActivity.class);

                intent.putExtra("bolName", bolItem);
                intent.putExtra("fakName", fakItem);
                intent.putExtra("uniName", uniItem);

                startActivity(intent);

            }


        }
    }

    private void getDataFirebase() {

        DatabaseReference newReference = firebaseDatabase.getReference("Universities");
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    HashMap<String, Object> hashMap = (HashMap<String, Object>) ds.getValue();
                    uniNamesFB.add((String) hashMap.get("uniname"));
                }

                //spinner uni
                ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, uniNamesFB);
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerUni.setAdapter(dataAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void getDataSpinnerFak() {

        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniItem);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    if (!ds.getKey().equals("uniname")) {

                        HashMap<String, Object> facultyMap = (HashMap<String, Object>) ds.getValue();
                        fakNamesFB.add((String) facultyMap.get("fakname"));

                    }
                }
                //spinner uni
                ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, fakNamesFB);
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerFak.setAdapter(dataAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void getDataSpinnerBol() {

        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniItem).child(fakItem);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    if (!ds.getKey().equals("fakname")) {

                        HashMap<String, Object> facultyMap = (HashMap<String, Object>) ds.getValue();
                        bolNamesFB.add((String) facultyMap.get("bolname"));

                    }
                }
                //spinner uni
                ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, bolNamesFB);
                dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerBol.setAdapter(dataAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    public void announcementsButtonClick(View view) {

        //Intent intent = new Intent(getApplicationContext(), AnnouncementsActivity.class);
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);


        startActivity(intent);

    }


}