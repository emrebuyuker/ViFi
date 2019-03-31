package com.salticusteam.vifi;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class CustomAdapter extends BaseAdapter {

    private LayoutInflater userInflater;
    private List<ListViewItem> userList;

    public CustomAdapter(Activity activity, List<ListViewItem> userList) {
        userInflater = (LayoutInflater) activity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        this.userList = userList;
    }

    @Override
    public int getCount() {
        return userList.size();
    }

    @Override
    public Object getItem(int i) {
        return userList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View lineView;
        lineView = userInflater.inflate(R.layout.custom_layout, null);
        TextView textViewSolUst = (TextView) lineView.findViewById(R.id.textViewSolUst);
        TextView textViewOrta = (TextView) lineView.findViewById(R.id.textViewOrta);
        TextView textViewSagAlt = (TextView) lineView.findViewById(R.id.textViewSagAlt);
        TextView textViewSolAlt = (TextView) lineView.findViewById(R.id.textViewSolAlt);

        ListViewItem user = userList.get(i);
        textViewSolUst.setText(user.getSolUst());
        textViewOrta.setText(user.getOrta());
        textViewSagAlt.setText(user.getSagAlt());
        textViewSolAlt.setText(user.getSolAlt());

        return lineView;
    }
}
