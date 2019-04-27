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
import android.widget.ProgressBar;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class UniversityFragment extends Fragment {

    FirebaseDatabase firebaseDatabase;

    ArrayList<String> uniNamesFB;

    ListView listView;

    ProgressBar spinner;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        firebaseDatabase = FirebaseDatabase.getInstance();
        uniNamesFB = new ArrayList<String>();

        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View customView = layoutInflater.inflate(R.layout.fragment_university, container, false);

        listView = customView.findViewById(R.id.listView);

        spinner = (ProgressBar) customView.findViewById(R.id.progressBarUniversity);

        getDataFirebase();
        listViewOnClick();

        return customView;
    }

    private void getDataFirebase() {

        DatabaseReference newReference = firebaseDatabase.getReference("Universities");
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    HashMap<String, Object> hashMap = (HashMap<String, Object>) ds.getValue();
                    uniNamesFB.add((String) hashMap.get("uniname"));
                }

                ArrayAdapter<String> veriAdaptoru = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, android.R.id.text1, uniNamesFB);
                listView.setAdapter(veriAdaptoru);
                spinner.setVisibility(View.INVISIBLE);
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

                String uniName = uniNamesFB.get(position);
                System.out.println("uniName= " + uniName);

                Fragment selectedFragment = new FacultiesFragment();
                Bundle bundle = new Bundle();
                bundle.putString("uniName", uniName);
                selectedFragment.setArguments(bundle);

                getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, selectedFragment).commit();

            }
        });
    }

    ;
}

