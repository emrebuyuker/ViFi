package com.salticusteam.vifi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ParseObject;
import com.parse.SaveCallback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Main4Activity extends AppCompatActivity {

    ImageView imageView;
    EditText addTextUniName;
    EditText addTextFakName;
    EditText addTextBolName;
    EditText addTextLessonName;
    Bitmap chosenImage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main4);

        imageView =(ImageView) findViewById(R.id.imageView);
        addTextUniName = findViewById(R.id.addEditTextUniName);
        addTextFakName = findViewById(R.id.addEditTextFakName);
        addTextBolName = findViewById(R.id.addEditTextBolName);
        addTextLessonName = findViewById(R.id.addEditTextLessonName);

    }


    public void addLessonSave (View view){

        String addUniName = this.addTextUniName.getText().toString();
        String addFakName = this.addTextFakName.getText().toString();
        String addBolName = this.addTextBolName.getText().toString();
        String addLessonName = this.addTextLessonName.getText().toString();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        chosenImage.compress(Bitmap.CompressFormat.PNG,50,byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();

        ParseFile parseFile = new ParseFile("image.png",bytes);

        ParseObject object = new ParseObject("Universities");
        object.put("image" , parseFile);
        object.put("uniname" , addUniName);
        object.put("fakname" , addFakName);
        object.put("bolname" , addBolName);
        object.put("lessonname" , addLessonName);
        object.saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {

                if (e != null) {
                    Toast.makeText(getApplicationContext(),e.getLocalizedMessage(),Toast.LENGTH_LONG).show();
                }else {
                    Toast.makeText(getApplicationContext(),"Post Uploaded",Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(getApplicationContext(),Main4Activity.class);
                    startActivity(intent);
                }

            }
        });
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public void addLessonSelect (View view){

        if (ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){

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

            Uri uri = data.getData();

            try {

                chosenImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(),uri);
                imageView.setImageBitmap(chosenImage);

            } catch (IOException e) {

                e.printStackTrace();

            }

        }

        super.onActivityResult(requestCode, resultCode, data);

    }

    public void onActivityMainactivity(View view) {

        Intent intent = new Intent(getApplicationContext(),MainActivity.class);
        startActivity(intent);

    }
}
