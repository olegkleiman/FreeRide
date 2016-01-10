package com.labs.okey.freeride.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.Ride;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.utils.RoundedDrawable;
import com.labs.okey.freeride.views.LayoutRipple;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Created by eli max on 22/06/2015.
 */
public class MyRidesAdapter extends RecyclerView.Adapter<MyRidesAdapter.ViewHolder> {

    private List<Ride> items;
    IRecyclerClickListener mClickListener;
    Context context;
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
    public void onBindViewHolder(ViewHolder holder, int position) {

        Ride ride = items.get(position);


        if(!ride.getDriverId().equals( Globals.userID))
        {
            holder.driverName.setText(ride.getDriverName());
            holder.ApprovedSing.setVisibility(View.GONE);
            holder.SteeringWheel.setVisibility(View.GONE);
        }
        else {

            holder.driverName.setVisibility(View.GONE);
            //TODO I hide the SteeringWheel
            holder.SteeringWheel.setVisibility(View.GONE);

            int approveStatus = ride.getApproved();

            if( approveStatus == Globals.RIDE_STATUS.WAIT .ordinal()) {
                holder.ApprovedSing.setImageResource(R.drawable.attention_26);
            } else if( approveStatus == Globals.RIDE_STATUS.APPROVED.ordinal()
                   || approveStatus == Globals.RIDE_STATUS.APPROVED_BY_SELFY.ordinal() ){
                holder.ApprovedSing.setImageResource(R.drawable.v_sing_26);
            } else if ( ride.getApproved() == Globals.RIDE_STATUS.DENIED.ordinal()
                   ||approveStatus == Globals.RIDE_STATUS.APPEAL.ordinal() ) {
                holder.ApprovedSing.setImageResource(R.drawable.ex_sing_26);
            }


            try {
                User user = User.load(context);



                Drawable drawable =
                        (Globals.drawMan.userDrawable(context,
                                "1",
                                user.getPictureURL())).get();
                if( drawable != null ) {
                    drawable = RoundedDrawable.fromDrawable(drawable);
                    ((RoundedDrawable) drawable)
                            .setCornerRadius(Globals.PICTURE_CORNER_RADIUS)
                            .setBorderColor(Color.WHITE)
                            .setBorderWidth(Globals.PICTURE_BORDER_WIDTH)
                            .setOval(true);

                    holder.DriverImage.setImageDrawable(drawable);

                }
            } catch (Exception e) {
                //TODO LOG_TAG is from main activity, nedd
                Log.e(LOG_TAG, e.getMessage());
            }

        }



        if( ride.getCreated() != null ) {
            DateFormat df = new SimpleDateFormat("MM.dd.yy");
            holder.created.setText(df.format(ride.getCreated()));
        }

    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        ImageView DriverImage;
        ImageView ApprovedSing;
        ImageView SteeringWheel;
        TextView driverName;
        TextView carNumber;
        TextView created;
        LayoutRipple rowLayout;

        IRecyclerClickListener mClickListener;

        public ViewHolder(View itemView,
                          IRecyclerClickListener clickListener) {
            super(itemView);

            mClickListener = clickListener;
            DriverImage = (ImageView) itemView.findViewById(R.id.imageDriver);
            ApprovedSing = (ImageView) itemView.findViewById(R.id.ApprovedSing);
            SteeringWheel = (ImageView) itemView.findViewById(R.id.SteeringWheel);
            driverName = (TextView) itemView.findViewById(R.id.txtDriverName);
            created = (TextView) itemView.findViewById(R.id.txtCreated);
            rowLayout = (LayoutRipple) itemView.findViewById(R.id.myRideRaw);

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
