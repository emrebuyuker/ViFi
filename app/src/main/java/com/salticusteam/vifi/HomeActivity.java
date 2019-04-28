package com.salticusteam.vifi;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class HomeActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    //private TextView mTextMessage;

    Context context = this;

    RelativeLayout relativeLayoutUniversity;

    FirebaseDatabase firebaseDatabase;

    ArrayList<String> uniNamesFB;

    TextView textViewUniversity;

    //ListView listView;

    private BottomNavigationView.OnNavigationItemSelectedListener onNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {

            Fragment selectedFragment = null;

            switch (item.getItemId()) {
                case R.id.university:
                    System.out.println("emre= " + R.id.university);

                    selectedFragment = new UniversityFragment();
                    break;
                case R.id.faculty:
                    selectedFragment = new DepartmentsFragment();
                    break;
                case R.id.chapter:
                    selectedFragment = new DepartmentsFragment();
                    break;
            }

            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container,selectedFragment).commit();

            return true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Intent intent = getIntent();

        final String uniName = intent.getStringExtra("uniName");
        System.out.println("uniNameadfasd= "+uniName);


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("");

        //relativeLayoutUniversity = findViewById(R.id.layoutUniversity);
        //textViewUniversity = findViewById(R.id.textViewUniversity);

        //relativeLayoutUniversity.setBackgroundColor(Color.WHITE);
        //textViewUniversity.setTextColor(Color.parseColor("#FB920F"));

        firebaseDatabase = FirebaseDatabase.getInstance();

        //listView = findViewById(R.id.list);

        uniNamesFB = new ArrayList<String>();

        /*mTextMessage = (TextView) findViewById(R.id.message);
        mTextMessage.setCursorVisible(false);*/
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new UniversityFragment()).commit();

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //getDataFirebase();

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.add_lesson, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.add_lesson) {

            Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
            startActivity(intent);

        }

        return super.onOptionsItemSelected(item);
    }

    protected void shareMyMessage(String _message) {

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, _message);
        startActivity(Intent.createChooser(share, "Uygulama linkini paylaş !! "));
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_slideshow) {

            Intent intent = new Intent(getApplicationContext(), AnnouncementsActivity.class);
            startActivity(intent);

        } else if (id == R.id.nav_share) {

            String message = "https://play.google.com/store/apps/details?id=com.salticusteam.vifi";
            shareMyMessage(message);

        }

        //DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        //drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
