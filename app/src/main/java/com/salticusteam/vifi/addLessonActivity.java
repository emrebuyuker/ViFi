package com.salticusteam.vifi;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class addLessonActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main5);
    }

    public void addLessonSelect (View view) {

    }

    public void addLessonSave (View view) {

    }



    public void onActivityMainactivity(View view) {

        Intent intent = new Intent(getApplicationContext(),MainActivity.class);
        startActivity(intent);

    }
}
