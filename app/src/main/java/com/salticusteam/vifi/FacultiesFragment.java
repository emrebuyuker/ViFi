package com.salticusteam.vifi;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class FacultiesFragment extends Fragment {

    FirebaseDatabase firebaseDatabase;

    ListView listView;

    String uniItem ;

    ArrayList<String> fakNamesFB;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View customView = inflater.inflate(R.layout.fragment_faculties, container,false);

        Bundle bundle = this.getArguments();

        uniItem = bundle.getString("uniName");

        firebaseDatabase = FirebaseDatabase.getInstance();

        listView = customView.findViewById(R.id.listView);

        fakNamesFB = new ArrayList<String>();

        getDataFirebase();
        listViewOnClick();

        return customView;

    }

    private void getDataFirebase() {

        DatabaseReference newReference = firebaseDatabase.getReference("Universities").child(uniItem);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    if (!ds.getKey().equals("uniname")) {

                        HashMap<String, Object> facultyMap = (HashMap<String, Object>) ds.getValue();
                        fakNamesFB.add((String) facultyMap.get("fakname"));

                    }
                }
                ArrayAdapter<String> veriAdaptoru = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, fakNamesFB);
                listView.setAdapter(veriAdaptoru);
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

                String facName = fakNamesFB.get(position);
                System.out.println("facName= "+facName);

                Fragment selectedFragment = new DepartmentsFragment();
                Bundle bundle = new Bundle();
                bundle.putString("uniName",uniItem);
                bundle.putString("facName",facName);
                selectedFragment.setArguments(bundle);

                getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,selectedFragment).commit();

            }
        });
    };


}

