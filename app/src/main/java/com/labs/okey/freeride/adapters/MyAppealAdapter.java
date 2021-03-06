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
import com.labs.okey.freeride.model.Appeal;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.utils.RoundedDrawable;
import com.labs.okey.freeride.views.LayoutRipple;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
/**
 * Created by eli max on 01/11/2015.
 */


public class MyAppealAdapter extends RecyclerView.Adapter<MyAppealAdapter.ViewHolder> {

    private List<Appeal> items;
    IRecyclerClickListener mClickListener;
    Context context;
    private static final String LOG_TAG = "FR.MyAppealAdapter";

    public MyAppealAdapter(List<Appeal> objects) {
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
                .inflate(R.layout.rides_appeal_item, parent, false);

        return new ViewHolder(v, mClickListener);

    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        Appeal appeal = items.get(position);


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
                holder.driverName.setText(user.getFirstName() + user.getLastName());

            }
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }




        DateFormat df = new SimpleDateFormat("MM.dd.yy");
        holder.created.setText(df.format(appeal.getCreatedAt()));

    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        ImageView DriverImage;
        TextView driverName;
        TextView created;
        LayoutRipple rowLayout;

        IRecyclerClickListener mClickListener;

        public ViewHolder(View itemView,
                          IRecyclerClickListener clickListener) {
            super(itemView);

            mClickListener = clickListener;
            DriverImage = (ImageView) itemView.findViewById(R.id.imageDriver);
            driverName = (TextView) itemView.findViewById(R.id.txtDriverName);
            created = (TextView) itemView.findViewById(R.id.txtCreated);
            rowLayout = (LayoutRipple) itemView.findViewById(R.id.myAppealRaw);

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
