package com.salticusteam.vifi;

import android.app.Application;

import com.parse.Parse;

public class ParseStarterClass extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        //set log level
        Parse.setLogLevel(Parse.LOG_LEVEL_DEBUG);

        Parse.initialize(new Parse.Configuration.Builder(this)
            .applicationId("mYTbIZO7wK5Bj7EIgWZQ2iCvGGQpPK3Gzp9nmBce")
            .clientKey("fXuxBUpaV6t0xCte7cB5DCodC8eTCWys6m3g1ekE")
            .server("https://parseapi.back4app.com/")
            .build()
        );
    }
}
