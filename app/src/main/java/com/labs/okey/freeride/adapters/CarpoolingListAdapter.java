package com.labs.okey.freeride.adapters;


import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.PassByDate;

import java.text.DateFormat;
import java.util.List;

public class CarpoolingListAdapter extends RecyclerView.Adapter<CarpoolingListAdapter.ViewHolder>  {

    private List<PassByDate> items;
    Context mContext;

    public CarpoolingListAdapter(Context context, List<PassByDate> objects) {
        items = objects;
        mContext = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.passenger_item_row_with_imag, parent, false);

        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {


        PassByDate passByDate = items.get(position);
        DateFormat df = DateFormat.getDateTimeInstance();
        holder.DateRide.setText(df.format(passByDate.getDateRide()));

        if (passByDate.getAsDriver()){
            holder.AsDriver.setImageResource(R.drawable.passenger50);
        }
        else{
            holder.AsDriver.setImageResource(R.drawable.steering_wheel50);
        }

    }


    @Override
    public int getItemCount() {
        return items.size();
    }


    public static class ViewHolder extends RecyclerView.ViewHolder
    {

        ImageView AsDriver;
        TextView DateRide;


        public ViewHolder(View itemView) {
            super(itemView);


            AsDriver = (ImageView) itemView.findViewById(R.id.imagePass);
            DateRide = (TextView) itemView.findViewById(R.id.txtPassengerName);
        }


    }
}
