package com.salticusteam.vifi;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class PostClass extends ArrayAdapter<String> {


    private final ArrayList<String> postClassImageNameArrayList;
    private final ArrayList<String> postClassImageArrayList;
    private final Activity context;

    public PostClass(ArrayList<String> postClassImageNameArrayList, ArrayList<String> postClassImageArrayList, Activity context) {

        super(context,R.layout.custom_view,postClassImageNameArrayList);
        this.postClassImageNameArrayList = postClassImageNameArrayList;
        this.postClassImageArrayList = postClassImageArrayList;
        this.context = context;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        LayoutInflater layoutInflater = context.getLayoutInflater();
        View customView = layoutInflater.inflate(R.layout.custom_view,null,true);

        TextView postClassImageNameText = customView.findViewById(R.id.postImageName);
        ImageView postClassLessonImage = customView.findViewById(R.id.postLessonImage);

        postClassImageNameText.setText(postClassImageNameArrayList.get(position));
        Picasso.get().load(postClassImageArrayList.get(position)).into(postClassLessonImage);

        return customView;
    }
}
