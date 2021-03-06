package com.salticusteam.vifi;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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

public class DepartmentsFragment extends Fragment {

    FirebaseDatabase firebaseDatabase;

    ListView listView;

    String facItem;
    String uniItem;

    ArrayList<String> depNamesFB;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        Bundle bundle = this.getArguments();

        View customView = inflater.inflate(R.layout.fragment_departments, container, false);

        HomeActivity homeActivity = (HomeActivity) requireActivity();

        homeActivity.setBottomMenuItem(R.id.chapter);

        firebaseDatabase = FirebaseDatabase.getInstance();

        listView = customView.findViewById(R.id.listView);

        depNamesFB = new ArrayList<String>();

        uniItem = bundle.getString("uniName");

        if (uniItem == "HomeActivity") {
            Toast.makeText(getContext(), "Lütfen Fakültenizi Seçiniz !!!", Toast.LENGTH_LONG).show();

        } else {
            facItem = bundle.getString("facName");
            getDataFirebase();
            listViewOnClick();
        }

        return customView;

    }

    private void getDataFirebase() {

        DatabaseReference newReference = firebaseDatabase.getReference("Universitiess").child(uniItem).child(facItem);
        newReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    if (!ds.getKey().equals("fakname")) {

                        HashMap<String, Object> facultyMap = (HashMap<String, Object>) ds.getValue();
                        depNamesFB.add((String) facultyMap.get("bolname"));

                    }
                }

                final List<ListViewItemHomeActivity> depNames = new ArrayList<>();

                for (int i = 0; i < depNamesFB.size(); i++) {

                    depNames.add(new ListViewItemHomeActivity(depNamesFB.get(i)));

                }

                ListViewAdapter adapter = new ListViewAdapter(getActivity(), depNames);
                listView.setAdapter(adapter);

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

                String depName = depNamesFB.get(position);

                Fragment selectedFragment = new LessonFragment();
                Bundle bundle = new Bundle();
                bundle.putString("uniName", uniItem);
                bundle.putString("facName", facItem);
                bundle.putString("depName", depName);
                selectedFragment.setArguments(bundle);

                getActivity().getSupportFragmentManager().beginTransaction().addToBackStack(null).replace(R.id.fragment_container, selectedFragment).commit();

            }
        });
    }

    ;
}

