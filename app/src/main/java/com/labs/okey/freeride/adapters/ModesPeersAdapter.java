package com.labs.okey.freeride.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.FRMode;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.views.LayoutRipple;

import java.util.List;

/**
 * Created by Oleg on 22-Aug-15.
 */
public class ModesPeersAdapter extends RecyclerView.Adapter<ModesPeersAdapter.ViewHolder>{

    private List<FRMode>    items;
    private Context         mContext;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    View.OnClickListener[] mListeners = new View.OnClickListener[] {
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {


                }
            }
    };

    public ModesPeersAdapter(Context context, List<FRMode> objects) {
        items = objects;
        mContext = context;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent,
                                         int viewType) {
        View v;
        View.OnClickListener listener = null;

        if (viewType == TYPE_HEADER) { // Inflate header layout
            v = LayoutInflater.
                    from(parent.getContext()).
                    inflate(R.layout.modes_header, parent, false);
            listener = mListeners[TYPE_HEADER];
        } else { // Inflate row layout
            v = LayoutInflater.
                    from(parent.getContext()).
                    inflate(R.layout.modes_raw, parent, false);
        }

        assert ( mContext instanceof IRecyclerClickListener);

        return new ViewHolder((IRecyclerClickListener)mContext,
                v, viewType);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder,
                                 int position) {
        if (holder.holderId == TYPE_ITEM) {
            FRMode mode = items.get(position - 1);

            holder.modeName.setText(mode.getName());
            holder.modeImage.setImageDrawable(mContext
                    .getResources()
                    .getDrawable(mode.getimageId()));
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1; // +1 for caption
    }

    @Override
    public int getItemViewType(int position) {

        return( isPositionHeader(position) ) ? TYPE_HEADER : TYPE_ITEM;
    }

    private boolean isPositionHeader(int position) {
        return position == 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        int holderId;

        IRecyclerClickListener mClickListener;

        // Row views
        TextView modeName;
        ImageView modeImage;
        LayoutRipple rowLayout;
        //LinearLayout rowLayout;

        public ViewHolder(IRecyclerClickListener clickListener,
                          View itemLayoutView,
                          int viewType) {
            super(itemLayoutView);

            holderId = viewType;
            mClickListener = clickListener;

            if(viewType == TYPE_ITEM){
                modeName = (TextView) itemLayoutView.findViewById(R.id.mode_name);
                modeImage = (ImageView) itemLayoutView.findViewById(R.id.mode_image);
                rowLayout = (LayoutRipple) itemLayoutView.findViewById(R.id.mode_row);
                //rowLayout = (LinearLayout) itemLayoutView.findViewById(R.id.mode_row);
                rowLayout.setOnClickListener(this);
            }
        }

        @Override
        public void onClick(View v) {
            v.invalidate();
            int position = this.getLayoutPosition();
            if( mClickListener != null ) {
                mClickListener.clicked(v, position);
            }
        }
    }
}

