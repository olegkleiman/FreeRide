package com.labs.okey.freeride.myrides;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.labs.okey.freeride.AppealDetailsActivity;
import com.labs.okey.freeride.R;
import com.labs.okey.freeride.adapters.MyAppealAdapter;
import com.labs.okey.freeride.model.Appeal;
import com.labs.okey.freeride.model.Ride;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.IRecyclerClickListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * Created by eli max on 18/06/2015.
 */
public class RejectedMyRidesFragment extends Fragment {

    List<Appeal> mAppeals = new ArrayList<>();

    List<Ride> mRides = new ArrayList<>();
    private static final String ARG_POSITION = "position";
    private static RejectedMyRidesFragment FragmentInstance;
    MyAppealAdapter adapter;

    public static RejectedMyRidesFragment getInstance() {

        if (FragmentInstance == null ) {
            FragmentInstance = new RejectedMyRidesFragment();
//        Bundle b = new Bundle();
//        b.putInt(ARG_POSITION, position);
//        f.setArguments(b);
        }
        return FragmentInstance;
    }
    public void setAppeals(List<Appeal> appeals) {

        if (appeals == null || appeals.isEmpty())
            return;

        mAppeals.clear();
        mAppeals.addAll(appeals);
        sort();
        //FilteringApproveAndOtherDrivers();
    }

    public void updateAppeals(List<Appeal> appeals){

        if (appeals == null || appeals.isEmpty())
            return;

        mAppeals.clear();
        mAppeals.addAll(appeals);
        sort();

        //FilteringApproveAndOtherDrivers();

        adapter.notifyDataSetChanged();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_myride_general, container, false);

        RecyclerView recycler = (RecyclerView)rootView.findViewById(R.id.recyclerMyRides);
        recycler.setHasFixedSize(true);
        recycler.setLayoutManager(new LinearLayoutManager(getActivity()));
        recycler.setItemAnimator(new DefaultItemAnimator());

        adapter = new MyAppealAdapter(mAppeals);
        adapter.setOnClickListener(new IRecyclerClickListener() {


            @Override
            public void clicked(View v, int position) {

                Appeal currentAppeal = mAppeals.get(position);
                Intent intent = new Intent(getActivity(), AppealDetailsActivity.class);


                intent.putExtra("appeal",  currentAppeal);
                startActivity(intent);
            }
        });
        recycler.setAdapter(adapter);

        return rootView;
    }

    private  void FilteringApproveAndOtherDrivers(){
        List<Ride> tempList = new ArrayList<Ride>();
        String userId = Globals.userID;

        for (Ride ride : mRides ){

            if (ride.getApproved() == Globals.RIDE_STATUS.DENIED.ordinal() &&
                    ride.getDriverId().equals(userId)) {
                tempList.add(ride);
            }
        }
        mRides.clear();
        mRides.addAll(tempList);
    }

    private void sort(){

        Collections.sort(mRides, new Comparator<Ride>() {
            public int compare(Ride r1, Ride r2) {
                return r1.getCreated().compareTo(r2.getCreated());
            }
        });
    }
}
