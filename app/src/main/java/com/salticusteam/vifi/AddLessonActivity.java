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
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.util.UUID;

public class AddLessonActivity extends AppCompatActivity {

    ImageView imageView;
    EditText addTextUniName;
    EditText addTextFakName;
    EditText addTextBolName;
    EditText addTextLessonName;
    EditText addTextType;
    EditText addTextImageName;

    Uri selected;

    private StorageReference mStorageRef;

    FirebaseDatabase database;
    DatabaseReference myRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_lesson);


        imageView = (ImageView) findViewById(R.id.imageView);
        addTextUniName = findViewById(R.id.addEditTextUniName);
        addTextFakName = findViewById(R.id.addEditTextFakName);
        addTextBolName = findViewById(R.id.addEditTextBolName);
        addTextLessonName = findViewById(R.id.addEditTextBookName);
        addTextType = findViewById(R.id.editTextPdfJpg);
        addTextImageName = findViewById(R.id.addEditTextImageName);

        mStorageRef = FirebaseStorage.getInstance().getReference();

        database = FirebaseDatabase.getInstance();
        myRef = database.getReference();

    }

    public void addLessonImagesSelect(View view) {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 2);

        } else {

            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, 1);

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 2) {

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, 1);

            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {

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

    public void addLessonSave(View view) {

        final UUID uuidImage = UUID.randomUUID();

        String imageName = "imagess/" + uuidImage + ".jpg";

        StorageReference storageReference = mStorageRef.child(imageName);

        storageReference.putFile(selected).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {

            @SuppressWarnings("VisibleForTests")

            @Override

            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                StorageReference newImageRef = FirebaseStorage.getInstance().getReference("imagess/" + uuidImage + ".jpg");

                newImageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {

                    @Override

                    public void onSuccess(Uri uri) {

                        String downloadURL = uri.toString();

                        String userUniName = addTextUniName.getText().toString();
                        String userFakName = addTextFakName.getText().toString();
                        String userBolName = addTextBolName.getText().toString();
                        String userLessonName = addTextLessonName.getText().toString();
                        String userImageName = addTextImageName.getText().toString();
                        String userType = addTextType.getText().toString();

                        UUID uuid = UUID.randomUUID();
                        String uuidString = uuid.toString();


                        myRef.child("Universities").child(userUniName).child("uniname").setValue(userUniName);
                        myRef.child("Universities").child(userUniName).child(userFakName).child("fakname").setValue(userFakName);
                        myRef.child("Universities").child(userUniName).child(userFakName).child(userBolName).child("bolname").setValue(userBolName);
                        myRef.child("Universities").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child("lessonname").setValue(userLessonName);
                        myRef.child("Universities").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child(userImageName).child("imagename").setValue(userImageName);
                        myRef.child("Universities").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child(userImageName).child(userType).child("type").setValue(userType);
                        myRef.child("Universities").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child(userImageName).child(userType).child(uuidString).child("downloadURL").setValue(downloadURL);

                        Toast.makeText(getApplicationContext(), "Lesson Saved", Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(getApplicationContext(), AddLessonActivity.class);
                        startActivity(intent);
                    }

                });

            }

        }).addOnFailureListener(new OnFailureListener() {

            @Override

            public void onFailure(@NonNull Exception e) {

                Toast.makeText(getApplicationContext(), e.getLocalizedMessage().toString(), Toast.LENGTH_LONG).show();

            }
        });

    }

    public void onActivityMainactivity(View view) {

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);

    }

    public void onActivityPDF(View view) {

        Intent intent = new Intent(getApplicationContext(), AddPdfLessonActivity.class);
        startActivity(intent);

    }
}
