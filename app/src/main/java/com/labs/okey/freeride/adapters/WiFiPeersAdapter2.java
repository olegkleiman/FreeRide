package com.labs.okey.freeride.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.wifi.p2p.WifiP2pDevice;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.WifiP2pDeviceUser;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.utils.IRefreshable;
import com.labs.okey.freeride.utils.RoundedDrawable;


import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Created by Oleg Kleiman on 23-May-15.
 */
public class WiFiPeersAdapter2 extends RecyclerView.Adapter<WiFiPeersAdapter2.ViewHolder>{

    private static final String LOG_TAG = "FR.DriversAdapter";

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<WifiP2pDeviceUser> items;
    private Context mContext;
    private int mHeaderLayoutId;
    private int mRowLayoutId;

    public WiFiPeersAdapter2(Context context,
                             int headerLayoutId,
                             int rowLayoutId,
                             List<WifiP2pDeviceUser> objects){
        mContext = context;
        mHeaderLayoutId = headerLayoutId;
        mRowLayoutId = rowLayoutId;
        items = objects;
    }

    View.OnClickListener[] mListeners = new View.OnClickListener[] {
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    IRefreshable refreshable =
                            (mContext instanceof IRefreshable) ?
                            (IRefreshable)mContext :
                             null;
                    if( refreshable != null )
                        refreshable.refresh();
                }
            }
    };

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View v;
        View.OnClickListener listener = null;

        if (viewType == TYPE_HEADER) { // Inflate header layout
            v = LayoutInflater.
                    from(parent.getContext()).
                    inflate(mHeaderLayoutId, parent, false);
            listener = mListeners[TYPE_HEADER];
        } else { // Inflate row layout
            v = LayoutInflater.
                    from(parent.getContext()).
                    inflate(mRowLayoutId, parent, false);
        }

        IRecyclerClickListener recyclerClickListener =
                ( mContext instanceof IRecyclerClickListener ) ?
                        (IRecyclerClickListener)mContext :
                         null;

        return new ViewHolder(mContext,
                            recyclerClickListener,
                            v, viewType, listener);

    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        if (holder.holderId == TYPE_ITEM) {

            WifiP2pDeviceUser device = items.get(position - 1);

            holder.txtDriverName.setText(device.getUserName());
            if( holder.txtRideCode != null )
                holder.txtRideCode.setText(device.getRideCode());
            //holder.deviceStatus.setText(getDeviceStatus(device.status));
            holder.setImageStatus(device.status);

            String userId = device.getUserId();
            String pictureURL = getUserPictureURL(userId);

            Drawable drawable = null;
            try {
                drawable = (Globals.drawMan.userDrawable(mContext,
                        userId,
                        pictureURL)).get(); // May be null because of pictureURL
                                            // but handled in catch block
                if( drawable != null ) {

                    drawable = RoundedDrawable.fromDrawable(drawable);
                    ((RoundedDrawable) drawable)
                            .setCornerRadius(Globals.PICTURE_CORNER_RADIUS)
                            .setBorderColor(Color.LTGRAY)
                            .setBorderWidth(Globals.PICTURE_BORDER_WIDTH)
                            .setOval(true);

                    holder.userPicture.setImageDrawable(drawable);
                }
            } catch (InterruptedException | ExecutionException e) {
                Log.e(LOG_TAG, e.getMessage());
            } catch (NullPointerException ex) {
                Log.e(LOG_TAG, "No drawable for pictureURL");
            }
        }

    }

    @Override
    public int getItemCount() {
        return items.size() + 1;  // +1 for header view
    }

    // With the following method we check what type of view is being passed
    // In case of drawer adapter, each item corresponds to different type,
    // According to this item type (i.e. item position) different click listeners
    // will be attached to view items
    @Override
    public int getItemViewType(int position) {

        return( isPositionHeader(position) ) ? TYPE_HEADER : TYPE_ITEM;
    }

    private boolean isPositionHeader(int position) {
        return position == 0;
    }

    public void add(WifiP2pDeviceUser device){
        if( !items.contains(device) )
            items.add(device);
    }

    public void updateItem(WifiP2pDeviceUser device){
        int index = items.indexOf(device);
        if( index != -1 )
            items.set(index, device);
        else
            items.add(device);
    }

    private String getDeviceStatus(int deviceStatus) {
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";

        }
    }

    private String getUserPictureURL(String userId){
        if( userId == null || userId.isEmpty() )
            return "";

        String[] tokens = userId.split(":");
        if( tokens.length > 1 ){
            if( Globals.FB_PROVIDER.equals(tokens[0]) ) {
                return "http://graph.facebook.com/" + tokens[1] + "/picture?type=large";
            } else {
                return "";
            }
        } else
            return "";
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener{

        int holderId;
        IRecyclerClickListener mClickListener;

        // Header views
        ImageButton btnRefresh;

        // Row views
        TextView txtDriverName;
        TextView txtRideCode;
        TextView deviceStatus;
        ImageView userPicture;
        RelativeLayout rowLayout;
        ImageView imageStatus;

        Drawable drawableAvailable;
        Drawable drawableConnected;

        public ViewHolder(Context context,
                          IRecyclerClickListener clickListener,
                          View itemLayoutView,
                          int viewType,
                          View.OnClickListener listener) {
            super(itemLayoutView);
            mClickListener = clickListener;

            if(viewType == TYPE_HEADER) {

                btnRefresh = (ImageButton)itemLayoutView.findViewById(R.id.btnRefresh);
                btnRefresh.setOnClickListener(listener);

            } else if(viewType == TYPE_ITEM){

                holderId = viewType;

                txtDriverName = (TextView) itemLayoutView.findViewById(R.id.txt_peer_name);
                txtRideCode = (TextView) itemLayoutView.findViewById(R.id.txt_ride_code);
                userPicture = (ImageView) itemLayoutView.findViewById(R.id.userPicture);
                rowLayout = (RelativeLayout)itemLayoutView.findViewById(R.id.device_row);
                imageStatus = (ImageView) itemLayoutView.findViewById(R.id.imgStatus);
                rowLayout.setOnClickListener(this);

                drawableAvailable = context.getResources().getDrawable(R.drawable.ic_action_disconnected);
                drawableConnected = context.getResources().getDrawable(R.drawable.accept_24);

            }

        }

        @Override
        public void onClick(View v) {
            int position = this.getLayoutPosition();
            --position; // header starts at position 0
            if( mClickListener != null ) {
                mClickListener.clicked(v, position);
            }
        }

        public void setImageStatus(int deviceStatus) {

            Drawable drawable;

            switch (deviceStatus) {
                case WifiP2pDevice.AVAILABLE:
                    drawable = drawableAvailable;
                    break;
                case WifiP2pDevice.CONNECTED:
                    drawable = drawableConnected;
                    break;

                default:
                    drawable = null;

            }

            if( drawable != null
                    && imageStatus != null ) {
                imageStatus.setImageDrawable(drawable);
            }
        }
    }
}
