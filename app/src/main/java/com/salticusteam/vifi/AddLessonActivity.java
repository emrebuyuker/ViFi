package com.salticusteam.vifi;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
        // ACTION_PICK grants read access to the picked URI; no storage permission is needed (and none is granted on API 33+).
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 1);
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


                        myRef.child("Universitiess").child(userUniName).child("uniname").setValue(userUniName);
                        myRef.child("Universitiess").child(userUniName).child(userFakName).child("fakname").setValue(userFakName);
                        myRef.child("Universitiess").child(userUniName).child(userFakName).child(userBolName).child("bolname").setValue(userBolName);
                        myRef.child("Universitiess").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child("lessonname").setValue(userLessonName);
                        myRef.child("Universitiess").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child(userImageName).child("imagename").setValue(userImageName);
                        myRef.child("Universitiess").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child(userImageName).child(userType).child("type").setValue(userType);
                        myRef.child("Universitiess").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child(userImageName).child(userType).child(uuidString).child("downloadURL").setValue(downloadURL);

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
