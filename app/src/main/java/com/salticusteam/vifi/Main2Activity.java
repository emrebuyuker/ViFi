package com.salticusteam.vifi;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class Main2Activity extends AppCompatActivity {

    ListView listView ;
    String lessonName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        listView = (ListView) findViewById(R.id.listView);
        TextView textView = (TextView) findViewById(R.id.textViewTitle);


        Intent intent = getIntent();

        final String uniName = intent.getStringExtra("uniName");
        final String fakName = intent.getStringExtra("fakName");
        final String bolName = intent.getStringExtra("bolName");

        textView.setText(bolName);


        if (uniName.equals("SAKARYA ÜNİVERSİTESİ") && fakName.equals("BİLGİSAYAR VE BİLİŞİM BİLİMLERİ FAKÜLTESİ") && bolName.equals("BİLGİSAYAR BÖLÜMÜ")){
            sauBilgisayar();
        }
        else if (uniName.equals("SAKARYA ÜNİVERSİTESİ") && fakName.equals("FEN EDEBİYAT FAKÜLTESİ") && bolName.equals("MATEMATİK BÖLÜMÜ")){
            sauMatematik();
        }
        else if (uniName.equals("SAKARYA ÜNİVERSİTESİ") && fakName.equals("MÜHENDİSLİK FAKÜLTESİ") && bolName.equals("ELEKTRİK ELEKTRONİK MÜHENDİSLİĞİ")) {
            sauEEM();
        }
        else {
            Toast.makeText(getApplicationContext(),"Bölümüzün dersleri en kısa sürede yüklenecektir lütfen beklemede kalınız :)",Toast.LENGTH_LONG).show();
        }

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                //Main3Activity Start and Send Out Data

                Intent intent = new Intent(getApplicationContext(),Main3Activity.class);

                lessonName = listView.getItemAtPosition(position).toString();

                intent.putExtra("lessonName",lessonName);
                startActivity(intent);

            }
        });
    }

    private void sauEEM(){

        //listview add lesson

        ArrayList<String> lessonNameSauEEM = new ArrayList<String>();
        lessonNameSauEEM.add("SAKARYA ÜNİVERSİTESİ");
        lessonNameSauEEM.add("MÜHENDİSLİK FAKÜLTESİ");
        lessonNameSauEEM.add("ELEKTRİK ELEKTRONİK MÜHENDİSLİĞİ");
        lessonNameSauEEM.add("DERSLERİ");
        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1,lessonNameSauEEM);
        listView.setAdapter(arrayAdapter);

    }

    private void sauBilgisayar(){

        //listview add lesson

        ArrayList<String> lessonNameSauBilgisayar = new ArrayList<String>();
        lessonNameSauBilgisayar.add("SAKARYA ÜNİVERSİTESİ");
        lessonNameSauBilgisayar.add("BİLGİSAYAR VE BİLİŞİM BİLİMLERİ FAKÜLTESİ");
        lessonNameSauBilgisayar.add("BİLGİSAYAR BÖLÜMÜ");
        lessonNameSauBilgisayar.add("DERSLERİ");
        ArrayAdapter arrayAdapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1,lessonNameSauBilgisayar);
        listView.setAdapter(arrayAdapter);

    }

    private void sauMatematik(){

        //listview add lesson

        ArrayList<String> lessonNameMatematik = new ArrayList<String>();
        lessonNameMatematik.add("SAKARYA ÜNİVERSİTESİ");
        lessonNameMatematik.add("FEN EDEBİYAT FAKÜLTESİ");
        lessonNameMatematik.add("MATEMATİK BÖLÜMÜ");
        lessonNameMatematik.add("DERSLERİ");
        ArrayAdapter arrayAdapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1,lessonNameMatematik);
        listView.setAdapter(arrayAdapter);

    }
}
