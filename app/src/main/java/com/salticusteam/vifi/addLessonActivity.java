package com.salticusteam.vifi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import java.io.IOException;

public class addLessonActivity extends AppCompatActivity {

    ImageView imageView;
    EditText addTextUniName;
    EditText addTextFakName;
    EditText addTextBolName;
    EditText addTextLessonName;

    Uri selected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main5);


        imageView =(ImageView) findViewById(R.id.imageView);
        addTextUniName = findViewById(R.id.addEditTextUniName);
        addTextFakName = findViewById(R.id.addEditTextFakName);
        addTextBolName = findViewById(R.id.addEditTextBolName);
        addTextLessonName = findViewById(R.id.addEditTextLessonName);

    }

    public void addLessonImagesSelect(View view) {

        if (ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},2);

        }else{

            Intent intent = new Intent(Intent.ACTION_PICK , MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent,1);

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 2){

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Intent intent = new Intent(Intent.ACTION_PICK , MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent,1);

            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == RESULT_OK && data != null){

            selected = data.getData();

            try {

                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selected);
                imageView.setImageBitmap(bitmap);

            } catch (IOException e) {

                e.printStackTrace();

            }

        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void addLessonSave (View view) {

    }

    public void onActivityMainactivity(View view) {

        Intent intent = new Intent(getApplicationContext(),MainActivity.class);
        startActivity(intent);

    }
}
