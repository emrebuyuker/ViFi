package com.salticusteam.vifi;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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

public class LessonFragment extends Fragment {

    FirebaseDatabase firebaseDatabase;

    ListView listView;

    String facItem;
    String uniItem;
    String depItem;
    String lessonName;

    ArrayList<String> lessonNamesFB;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        Bundle bundle = this.getArguments();

        View customView = inflater.inflate(R.layout.fragment_lesson, container, false);

        BottomNavigationView mBottomNavigationView=(BottomNavigationView)customView.findViewById(R.id.navigation);

        View view = mBottomNavigationView.findViewById(R.id.lesson);
        view.performClick();

        firebaseDatabase = FirebaseDatabase.getInstance();

        listView = customView.findViewById(R.id.listView);

        lessonNamesFB = new ArrayList<String>();

        uniItem = bundle.getString("uniName");

        if(uniItem == "HomeActivity"){
            Toast.makeText(getContext(), "Lütfen Üniversetiniz Seçiniz !!!", Toast.LENGTH_LONG).show();

        }else{
            facItem = bundle.getString("facName");
            depItem = bundle.getString("depName");
            getDataFirebase();
            listViewOnClick();
        }

        return customView;

    }

    private void getDataFirebase() {

        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniItem).child(facItem).child(depItem);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    System.out.println("dataSnapshot= " + dataSnapshot);

                    if (!ds.getKey().equals("bolname")) {

                        HashMap<String, Object> facultyMap = (HashMap<String, Object>) ds.getValue();
                        lessonNamesFB.add((String) facultyMap.get("lessonname"));

                    }
                }

                final List<ListViewItemHomeActivity> lessonNames = new ArrayList<>();

                for (int i=0 ; i<lessonNamesFB.size(); i++){

                    lessonNames.add(new ListViewItemHomeActivity(lessonNamesFB.get(i)) );

                }

                ListViewAdapter adapter = new ListViewAdapter(getActivity(), lessonNames);
                listView.setAdapter(adapter);

                /*ArrayAdapter<String> veriAdaptoru = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, lessonNamesFB);
                listView.setAdapter(veriAdaptoru);*/

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void listViewOnClick() {

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                lessonName = lessonNamesFB.get(position);
                System.out.println("parent= " + lessonName);

                Intent intent2 = new Intent(getActivity(), CourseExamListActivity.class);

                intent2.putExtra("uniName", uniItem);
                intent2.putExtra("fakName", facItem);
                intent2.putExtra("bolName", depItem);
                intent2.putExtra("lesson", lessonName);

                startActivity(intent2);

            }
        });

    };
}
