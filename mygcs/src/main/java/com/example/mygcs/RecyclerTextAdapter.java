package com.example.mygcs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class RecyclerTextAdapter extends RecyclerView.Adapter <RecyclerTextAdapter.ViewHolder> {
    private ArrayList<RecyclerItem> mData = null;

    RecyclerTextAdapter(ArrayList<RecyclerItem> list) {
        mData = list;
    }


    @NonNull
    @Override
    public RecyclerTextAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.recycler_item, parent, false);
        RecyclerTextAdapter.ViewHolder vh = new RecyclerTextAdapter.ViewHolder(view);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerTextAdapter.ViewHolder holder, int position) {
        RecyclerItem item = mData.get(position);

        holder.log.setText(item.getlog());
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView log;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            log = itemView.findViewById(R.id.log);
        }
    }
}
