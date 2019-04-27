package com.salticusteam.vifi;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.UUID;

public class AddPdfLessonActivity extends AppCompatActivity {

    EditText addTextUniName;
    EditText addTextFakName;
    EditText addTextBolName;
    EditText addTextLessonName;
    EditText addTextType;
    EditText addTextImageName;

    Button selectFile, upload, btnaddAnnouncements;
    TextView notification;

    FirebaseStorage storage;

    Uri selected;

    private StorageReference mStorageRef;

    FirebaseDatabase database;
    ProgressDialog progressDialog;
    DatabaseReference myRef;

    String downloadURL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_pdf_lesson);

        storage = FirebaseStorage.getInstance();

        selectFile = findViewById(R.id.selectFile);
        upload = findViewById(R.id.upload);
        btnaddAnnouncements = findViewById(R.id.buttonAddAnnouncements);

        notification = findViewById(R.id.notification);

        addTextUniName = findViewById(R.id.addEditTextUniName);
        addTextFakName = findViewById(R.id.addEditTextFakName);
        addTextBolName = findViewById(R.id.addEditTextBolName);
        addTextLessonName = findViewById(R.id.addEditTextBookName);
        addTextType = findViewById(R.id.editTextPdfJpg);
        addTextImageName = findViewById(R.id.addEditTextImageName);

        mStorageRef = FirebaseStorage.getInstance().getReference();

        database = FirebaseDatabase.getInstance();
        myRef = database.getReference();

        selectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (ContextCompat.checkSelfPermission(AddPdfLessonActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

                    selectPdf();

                } else {
                    ActivityCompat.requestPermissions(AddPdfLessonActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 9);
                }

            }
        });

        upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (selected != null) {
                    uploadFile(selected);
                } else {
                    Toast.makeText(AddPdfLessonActivity.this, "Select a file", Toast.LENGTH_SHORT).show();
                }


            }
        });

    }

    private void uploadFile(Uri selected) {

        final UUID uuidImage = UUID.randomUUID();

        String pdfName = "pdfs/" + uuidImage + ".pdf";

        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setTitle("Uploading file...");
        progressDialog.setProgress(0);
        progressDialog.show();

        StorageReference storageReference = mStorageRef.child(pdfName);
        storageReference.putFile(selected)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @SuppressWarnings("VisibleForTests")

                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                        StorageReference reference = FirebaseStorage.getInstance().getReference("pdfs/" + uuidImage + ".pdf");
                        reference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                downloadURL = uri.toString();

                                String userUniName = addTextUniName.getText().toString();
                                String userFakName = addTextFakName.getText().toString();
                                String userBolName = addTextBolName.getText().toString();
                                String userLessonName = addTextLessonName.getText().toString();
                                String userImageName = addTextImageName.getText().toString();
                                String userType = addTextType.getText().toString();

                                UUID uuid = UUID.randomUUID();
                                String uuidString = uuid.toString();

                                /*myRef.child("Universities").child(userUniName).child("uniname").setValue(userUniName);
                                myRef.child("Universities").child(userUniName).child(userFakName).child("fakname").setValue(userFakName);
                                myRef.child("Universities").child(userUniName).child(userFakName).child(userBolName).child("bolname").setValue(userBolName);
                                myRef.child("Universities").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child("lessonname").setValue(userLessonName);
                                myRef.child("Universities").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child(userImageName).child("imagename").setValue(userImageName);
                                myRef.child("Universities").child(userUniName).child(userFakName).child(userBolName).child(userLessonName).child(userImageName).child(uuidString).child("downloadURL").setValue(downloadURL);*/

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

                Toast.makeText(AddPdfLessonActivity.this, "File not succesfuly uploaded", Toast.LENGTH_SHORT).show();

            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {

                int currentProgress = (int) (100 * taskSnapshot.getBytesTransferred() / taskSnapshot.getTotalByteCount());
                progressDialog.setProgress(currentProgress);


            }
        });

    }

    private void selectPdf() {

        Intent intent = new Intent();
        intent.setType("application/pdf");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, 86);

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
        if (requestCode == 9 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            selectPdf();
        } else {
            Toast.makeText(AddPdfLessonActivity.this, "please provide permission", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 86 && resultCode == RESULT_OK && data != null) {
            selected = data.getData();
            notification.setText("A file is selected : " + data.getData().getLastPathSegment());

        } else {
            Toast.makeText(AddPdfLessonActivity.this, "Please select a file", Toast.LENGTH_SHORT).show();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void addAnnouncementsBtnClick(View view) {

        Intent intent = new Intent(getApplicationContext(), AddAnnouncements.class);
        startActivity(intent);

    }
}
