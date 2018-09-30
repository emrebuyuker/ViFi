package com.salticusteam.vifi;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

public class ImageZoomActivity extends AppCompatActivity {

    ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_zoom);

        imageView = findViewById(R.id.imageViewZoom);

        Intent intent = getIntent();

        final String seleckImageURL = intent.getStringExtra("seleckImageURL");

        Picasso.get().load(seleckImageURL).into(imageView);
    }
}
