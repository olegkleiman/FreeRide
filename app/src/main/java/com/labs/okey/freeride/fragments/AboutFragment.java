package com.labs.okey.freeride.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.labs.okey.freeride.R;

/**
 * Created by eli max on 24/01/2016.
 */
public class AboutFragment extends Fragment {

    private static AboutFragment FragmentInstance;

    public static AboutFragment getInstance() {

        if (FragmentInstance == null ) {
            FragmentInstance = new AboutFragment();
        }
        return FragmentInstance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }


    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {


        View rootView = inflater.inflate(R.layout.fragment_about_about, container, false);
        return  rootView;
    }
}
