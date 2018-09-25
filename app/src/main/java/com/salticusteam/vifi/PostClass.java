package com.salticusteam.vifi;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class PostClass extends ArrayAdapter<String> {


    private final ArrayList<String> postClassImageArrayList;
    private final Activity context;

    public PostClass(ArrayList<String> postClassImageArrayList, Activity context) {

        super(context,R.layout.custom_view,postClassImageArrayList);
        this.postClassImageArrayList = postClassImageArrayList;
        this.context = context;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        LayoutInflater layoutInflater = context.getLayoutInflater();
        View customView = layoutInflater.inflate(R.layout.custom_view,null,true);

        ImageView postClassLessonImage = customView.findViewById(R.id.postLessonImage);

        Picasso.get().load(postClassImageArrayList.get(position)).into(postClassLessonImage);

        return customView;
    }
}
