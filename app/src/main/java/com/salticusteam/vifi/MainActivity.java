package com.salticusteam.vifi;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.parse.FindCallback;
import com.parse.GetCallback;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.SaveCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    String uniItem;
    String fakItem;
    String bolItem;

    Spinner spinnerUni;
    Spinner spinnerFak;
    Spinner spinnerBol;

    Context context = this;


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.add_lesson,menu);

        return super.onCreateOptionsMenu(menu);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.add_lesson){

            Intent intent = new Intent(getApplicationContext(),AddLessonActivity.class);
            startActivity(intent);

        }

        return super.onOptionsItemSelected(item);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinnerUni = (Spinner) findViewById(R.id.spinnerUni);
        spinnerFak = (Spinner) findViewById(R.id.spinnerFak);
        spinnerBol = (Spinner) findViewById(R.id.spinnerBol);

        spinnerUni.setOnItemSelectedListener(this);

        spinnerPrivate();
        //parseDataLoading();
        //parseDataDowland();
        //parseDataFullDowland();
        // loadUniversities();
    }

    private void spinnerPrivate() {

        ParseQuery<ParseObject> query = ParseQuery.getQuery("Universities");
        query.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> objects, ParseException e) {

                if (e != null) {
                    e.printStackTrace();
                }else {

                    if (objects.size() > 0 ) {

                        Set<String> uniNameSet = new HashSet<>();

                        for (ParseObject object : objects ) {

                            String uniName = object.getString("uniname");

                            uniNameSet.add(uniName);

                        }
                        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(context  , android.R.layout.simple_spinner_dropdown_item, new ArrayList<String>(uniNameSet));
                        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinnerUni.setAdapter(dataAdapter);

                    }
                }

            }
        });








       /* ArrayList<String> uniName = new ArrayList<String>();

        int uniSize = uniNameSet.size();

        for (int i = 0; i <= uniSize ; i++){

            uniName.add(i,uniNameSet.toString());

        }
        System.out.println("set2 : " + uniName);
        for(University uni: Universities.getUniversities()) {
            uniName.add(uni.getName());
        }

        //Spinner uniName

        spinnerUni.setOnItemSelectedListener(this);
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, );
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUni.setAdapter(dataAdapter);*/


    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {


        uniItem = spinnerUni.getSelectedItem().toString();

       /* if (adapterView.getId() == R.id.spinnerUni) {
            for (University uni: Universities.getUniversities()) {
                if (uni.getName().equals(uniItem)) {
                    ArrayList<String> fakName = new ArrayList<String>();
                    for(Faculty faculty: uni.getFaculties()) {
                        fakName.add(faculty.getName());
                    }

                    spinnerFak.setOnItemSelectedListener(this);
                    ArrayAdapter<String> dataAdapter2 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, fakName);
                    dataAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerFak.setAdapter(dataAdapter2);
                }
            }

        } else if (adapterView.getId() == R.id.spinnerFak) {

            fakItem = spinnerFak.getSelectedItem().toString();

            for (University uni: Universities.getUniversities()) {
                if (uni.getName().equals(uniItem)) {

                    for (Faculty faculty: uni.getFaculties()) {
                        if (faculty.getName().equals(fakItem)) {

                            ArrayList<String> bolName = new ArrayList<String>();
                            for (Department department: faculty.getDepartments()) {
                                bolName.add(department.getName());
                            }

                            spinnerBol.setOnItemSelectedListener(this);
                            ArrayAdapter<String> dataAdapter3 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, bolName);
                            dataAdapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            spinnerBol.setAdapter(dataAdapter3);

                            bolItem = spinnerBol.getSelectedItem().toString();
                        }
                    }
                }
            }


        } else {

            bolItem = spinnerBol.getSelectedItem().toString();
        }*/
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {


    }

    public void onLoginButtonClick(View view){

        // change Main2Activity

        Intent intent = new Intent(getApplicationContext(),Main2Activity.class);

        intent.putExtra("bolName",bolItem);
        intent.putExtra("fakName",fakItem);
        intent.putExtra("uniName",uniItem);

        startActivity(intent);

    }

    private void parseDataLoading(){

        ParseObject object = new ParseObject("Fruits");
        
        object.put("name","apple");
        object.put("calories",100);
        object.saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if (e!=null) {
                    Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }else {
                    Toast.makeText(getApplicationContext(), "Object Saved", Toast.LENGTH_LONG).show();
                }
            }
        });

    }

    private void loadUniversities(){

        ParseObject object = new ParseObject("UniversityDefinitions");

            try {
                object.put("name", "Sakarya Universitesi");

                Map<String, List<String>> faculties = new HashMap<>();
                String[] sakaryaElectronicDepartment = {};
                faculties.put("Muhendislik Fakultesi", Arrays.asList("Elektrik Elektronk Muhendisligi", "Bilgisayar Muhendisligi"));
                faculties.put("Tip Fakultesi", Arrays.asList("Tip", "Eczacilik"));
                object.put("faculties", faculties);

                object.save();
            } catch (ParseException pe) {
                pe.printStackTrace();
            }


    }

    private void parseDataDowland () {

        ParseQuery<ParseObject> query = ParseQuery.getQuery("Fruits");
        query.getInBackground("wFJwrgryXO", new GetCallback<ParseObject>() {
            @Override
            public void done(ParseObject object, ParseException e) {

                if (e != null) {
                    e.printStackTrace();
                }else {
                    String objectName = object.getString("name");
                    int objectCalories = object.getInt("calories");

                    System.out.println("object name : " +objectName);
                    System.out.println("object calories : " + objectCalories);

                }
            }
        });
    }

    private  void parseDataFullDowland () {

        ParseQuery<ParseObject> query = ParseQuery.getQuery("Fruits");
        query.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> objects, ParseException e) {

                if (e != null) {
                    e.printStackTrace();
                }else {

                    if (objects.size() > 0 ) {

                        for (ParseObject object : objects ) {
                            String objectName = object.getString("name");
                            int objectCalories = object.getInt("calories");

                            System.out.println("object name : " +objectName);
                            System.out.println("object calories : " + objectCalories);
                        }

                    }

                }

            }
        });

    }
}