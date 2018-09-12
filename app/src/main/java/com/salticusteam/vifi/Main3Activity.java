package com.salticusteam.vifi;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

public class Main3Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);

        TextView textView = (TextView) findViewById(R.id.textView4);

        Intent intent = getIntent();

        final String lessonName = intent.getStringExtra("lessonName");
        textView.setText(lessonName);


    }
}
