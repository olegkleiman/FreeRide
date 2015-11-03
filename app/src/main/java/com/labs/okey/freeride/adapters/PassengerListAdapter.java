package com.labs.okey.freeride.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.IRecyclerClickListener;

import java.util.List;

/**
 * Created by eli max on 06/07/2015.
 */
public class PassengerListAdapter extends RecyclerView.Adapter<PassengerListAdapter.ViewHolder>  {

    private List<User> items;
    Context mContext;
    IRecyclerClickListener mClickListener;

    public PassengerListAdapter(Context context, List<User> objects) {
        items = objects;
        mContext = context;
    }

    public void setOnClickListener(IRecyclerClickListener listener) {
        mClickListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View v = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.passenger_item_row_with_imag, parent, false);

        return new ViewHolder(v,(IRecyclerClickListener) mContext);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        if( items == null )
            return;

        User user = items.get(position);

        holder.PassengerName.setText(user.getFirstName() + " " + user.getLastName());
    }

    @Override
    public int getItemCount() {
        if( items == null )
            return 0;

        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
        implements View.OnClickListener {

        ImageView PassengerImage;
        TextView PassengerName;
        LinearLayout rawLayout;

        IRecyclerClickListener mClickListener;


        public ViewHolder(View itemView,IRecyclerClickListener clickListener) {
            super(itemView);

            mClickListener = clickListener;

            PassengerImage = (ImageView) itemView.findViewById(R.id.imagePass);
            PassengerName = (TextView) itemView.findViewById(R.id.txtPassengerName);
            rawLayout = (LinearLayout) itemView.findViewById(R.id.PassRow);

        }

        @Override
        public void onClick(View v) {

        }
    }
}
