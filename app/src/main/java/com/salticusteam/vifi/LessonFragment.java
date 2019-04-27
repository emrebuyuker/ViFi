package com.salticusteam.vifi;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class LessonFragment extends Fragment {

    FirebaseDatabase firebaseDatabase;

    ListView listView;

    String facItem ;
    String uniItem ;
    String depItem;

    ArrayList<String> lessonNamesFB;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        Bundle bundle = this.getArguments();

        uniItem = bundle.getString("uniName");
        facItem = bundle.getString("facName");
        depItem = bundle.getString("depName");

        View customView = inflater.inflate(R.layout.fragment_departments, container,false);

        firebaseDatabase = FirebaseDatabase.getInstance();

        listView = customView.findViewById(R.id.listView);

        lessonNamesFB = new ArrayList<String>();

        getDataFirebase();
        listViewOnClick();

        return customView;

    }
    private void getDataFirebase() {

        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniItem).child(facItem).child(depItem);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    System.out.println("dataSnapshot= "+dataSnapshot);

                    if (!ds.getKey().equals("bolname")){

                        HashMap<String, Object> facultyMap = (HashMap<String, Object>) ds.getValue();
                        lessonNamesFB.add((String) facultyMap.get("lessonname"));

                    }
                }

                ArrayAdapter<String> veriAdaptoru = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, lessonNamesFB);
                listView.setAdapter(veriAdaptoru);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void listViewOnClick() {

        /*listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                String depName = depNamesFB.get(position);

                Fragment selectedFragment = new LessonFragment();
                Bundle bundle = new Bundle();
                bundle.putString("uniName",uniItem);
                bundle.putString("facName",facItem);
                bundle.putString("depName",depName);
                selectedFragment.setArguments(bundle);

                getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,selectedFragment).commit();

            }
        });*/
    };
}
