package com.labs.okey.freeride.adapters;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.WifiP2pDeviceUser;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.utils.IRefreshable;

import java.util.List;

/**
 * Created by Oleg on 17-Sep-15.
 */
public class PeersAdapter extends RecyclerView.Adapter<PeersAdapter.ViewHolder> {

    private static final String LOG_TAG = "FR.PeersAdapter";

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<WifiP2pDevice> items;
    private Context mContext;
    private int mHeaderLayoutId;

    public PeersAdapter(Context context,
                        int headerLayoutId,
                        List<WifiP2pDevice> objects) {
        mContext = context;
        mHeaderLayoutId = headerLayoutId;
        items = objects;
    }

    View.OnClickListener[] mListeners = new View.OnClickListener[]{
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    IRefreshable refreshable =
                            (mContext instanceof IRefreshable) ?
                                    (IRefreshable) mContext :
                                    null;
                    if (refreshable != null)
                        refreshable.refresh();
                }
            }
    };

    @Override
    public PeersAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
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
                    inflate(R.layout.row_passenger, parent, false);
        }

        IRecyclerClickListener recyclerClickListener =
                (mContext instanceof IRecyclerClickListener) ?
                        (IRecyclerClickListener) mContext :
                        null;

        return new ViewHolder(mContext,
                recyclerClickListener,
                v, viewType, listener);


    }

    @Override
    public void onBindViewHolder(PeersAdapter.ViewHolder holder,
                                 int position) {
        if (holder.holderId == TYPE_ITEM) {

            WifiP2pDevice device = items.get(position - 1);
            holder.txtPeerName.setText(device.deviceName);
        }

    }

    @Override
    public int getItemCount() {
        return items.size() + 1;  // +1 for header view
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        // Header views
        ImageButton btnRefresh;

        // Row views
        TextView txtPeerName;

        int holderId;
        IRecyclerClickListener mClickListener;

        public ViewHolder(Context context,
                          IRecyclerClickListener clickListener,
                          View itemLayoutView,
                          int viewType,
                          View.OnClickListener listener) {
            super(itemLayoutView);
            mClickListener = clickListener;

            if (viewType == TYPE_HEADER) {

                btnRefresh = (ImageButton) itemLayoutView.findViewById(R.id.btnRefresh);
                btnRefresh.setOnClickListener(listener);

            } else if (viewType == TYPE_ITEM) {

                holderId = viewType;
                txtPeerName = (TextView) itemLayoutView.findViewById(R.id.txt_peer_name);
            }

        }


        @Override
        public void onClick(View view) {

        }
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
}
