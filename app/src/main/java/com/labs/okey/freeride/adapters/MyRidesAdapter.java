package com.labs.okey.freeride.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.Ride;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.pkmmte.view.CircularImageView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by eli max on 22/06/2015.
 */
public class MyRidesAdapter extends RecyclerView.Adapter<MyRidesAdapter.ViewHolder> {

    private List<Ride>          items;
    IRecyclerClickListener      mClickListener;
    Context                     context;
    private static final String LOG_TAG = "FR.MyRidesAdapter";

    public MyRidesAdapter(List<Ride> objects) {
        items = objects;
    }

    public void setOnClickListener(IRecyclerClickListener listener) {
        mClickListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        context = parent.getContext();

        View v = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.rides_general_item, parent, false);

        return new ViewHolder(v, mClickListener);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {

        Ride ride = items.get(position);

        if(!ride.getDriverId().equals( Globals.userID))
        {
            holder.driverName.setText(ride.getDriverName());
            holder.approvedSign.setVisibility(View.GONE);
        }
        else
        {
            holder.driverName.setVisibility(View.GONE);

            int approveStatus = ride.getApproved();

            if( approveStatus == Globals.RIDE_STATUS.WAIT .ordinal()) {
                holder.approvedSign.setImageResource(R.drawable.attention_26);
            } else if( approveStatus == Globals.RIDE_STATUS.APPROVED.ordinal()
                   || approveStatus == Globals.RIDE_STATUS.APPROVED_BY_SELFY.ordinal() ){
                holder.approvedSign.setImageResource(R.drawable.v_sing_26);
            } else if ( ride.getApproved() == Globals.RIDE_STATUS.DENIED.ordinal()
                   ||approveStatus == Globals.RIDE_STATUS.APPEAL.ordinal() ) {
                holder.approvedSign.setImageResource(R.drawable.ex_sing_26);
            }

            try {
                User user = User.load(context);

                ImageLoader imageLoader = Globals.volley.getImageLoader();
                imageLoader.get(user.getPictureURL(), new ImageLoader.ImageListener() {
                    @Override
                    public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                        Bitmap bitmap = response.getBitmap();
                        holder.driverImage.setImageBitmap(bitmap);
                    }

                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                });

            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
            }

        }

        if( ride.getCreated() != null ) {
            //DateFormat df = DateFormat.getDateInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            holder.created.setText(sdf.format(ride.getCreated()));
        }

    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        CircularImageView   driverImage;
        ImageView           approvedSign;
        TextView            driverName;
        TextView            carNumber;
        TextView            created;
        View                rowLayout;

        IRecyclerClickListener mClickListener;

        public ViewHolder(View itemView,
                          IRecyclerClickListener clickListener) {
            super(itemView);

            mClickListener = clickListener;
            driverImage = (CircularImageView) itemView.findViewById(R.id.imageDriver);
            approvedSign = (ImageView) itemView.findViewById(R.id.approvedSign);
            driverName = (TextView) itemView.findViewById(R.id.txtDriverName);
            created = (TextView) itemView.findViewById(R.id.txtCreated);
            rowLayout = itemView.findViewById(R.id.user_details_card);
            rowLayout.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            v.invalidate();
            int position = this.getLayoutPosition();
            if (mClickListener != null) {
                mClickListener.clicked(v, position);
            }
        }
    }
}
