package com.salticusteam.vifi;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AnnouncementsActivity extends AppCompatActivity {

    FirebaseDatabase firebaseDatabase;

    ArrayList<String> titlesFB;
    ArrayList<String> bookNamesFB;
    ArrayList<String> explanationsFB;
    ArrayList<String> bookPricesFB;
    ArrayList<String> contactFB;

    ListView listView;

    Context context = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announcements);

        firebaseDatabase = FirebaseDatabase.getInstance();

        titlesFB = new ArrayList<String>();
        bookNamesFB = new ArrayList<String>();
        explanationsFB = new ArrayList<String>();
        bookPricesFB = new ArrayList<String>();
        contactFB = new ArrayList<String>();

        listView = findViewById(R.id.listView);

        getDataFirebaseAnnouncements();

        Button btnAddAnnouncements = (Button) findViewById(R.id.buttonAddAnnouncements);

        btnAddAnnouncements.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(AnnouncementsActivity.this, "Lütfen instagram sayfımız olan 'buyukeryazilim' dan dm atınız.", Toast.LENGTH_LONG).show();
            }
        });


    }

    private void getDataFirebaseAnnouncements() {

        System.out.println("dataSnapshotAnnouncements Start");

        DatabaseReference newReference = firebaseDatabase.getReference("Announcements");
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                System.out.println("dataSnapshotAnnouncements= " + dataSnapshot);

                for (DataSnapshot ds : dataSnapshot.getChildren()) {


                    if (!ds.getKey().equals("Announcements")) {

                        HashMap<String, Object> imageNameMap = (HashMap<String, Object>) ds.getValue();
                        titlesFB.add((String) imageNameMap.get("title"));
                        bookNamesFB.add((String) imageNameMap.get("bookName"));
                        explanationsFB.add((String) imageNameMap.get("acıklama"));
                        bookPricesFB.add((String) imageNameMap.get("lessonname"));
                        contactFB.add((String) imageNameMap.get("contact"));

                    }

                    System.out.println("a1 titles= " + titlesFB);
                    System.out.println("a1 bookNamesFB= " + bookNamesFB);
                    System.out.println("a1 explanationsFB= " + explanationsFB);
                    System.out.println("a1 bookPricesFB= " + bookPricesFB);
                }


                /*ArrayAdapter<String> veriAdaptoru2 = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, android.R.id.text1, bookNamesFB);
                listView.setAdapter(veriAdaptoru2);*/

                listViewAdapter();

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void listViewAdapter() {

        final List<ListViewItem> users = new ArrayList<>();

        for (int i = 0; i < titlesFB.size(); i++) {

            users.add(new ListViewItem(bookNamesFB.get(i), explanationsFB.get(i), bookPricesFB.get(i), contactFB.get(i)));

        }

        final ListView listView = (ListView) findViewById(R.id.listView);

        CustomAdapter adapter = new CustomAdapter(AnnouncementsActivity.this, users);
        listView.setAdapter(adapter);

    }
}
