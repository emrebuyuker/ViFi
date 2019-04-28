package com.salticusteam.vifi;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class ListViewAdapter extends BaseAdapter {

    private LayoutInflater inflater;
    private List<ListViewItemHomeActivity> list;

    public ListViewAdapter(Activity activity, List<ListViewItemHomeActivity> list) {
        inflater = (LayoutInflater) activity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        this.list = list;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int i) {
        return list.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View lineView;
        lineView = inflater.inflate(R.layout.list_adapter, null);
        TextView textViewFirst = (TextView) lineView.findViewById(R.id.textViewFirst);

        ListViewItemHomeActivity user = list.get(i);
        textViewFirst.setText(user.getFirst());

        return lineView;
    }
}
