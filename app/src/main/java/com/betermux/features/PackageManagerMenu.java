package com.betermux.features;

import android.content.Intent;
import android.view.View;

import com.termux.app.TermuxActivity;
import com.termux.app.activities.PackageManagerActivity;

/**
 * Handles the toolbar packages button — launches the full Package Manager activity.
 */
public class PackageManagerMenu {

    private final TermuxActivity mActivity;

    public PackageManagerMenu(TermuxActivity activity) {
        this.mActivity = activity;
    }

    public void show(View anchor) {
        Intent intent = new Intent(mActivity, PackageManagerActivity.class);
        mActivity.startActivity(intent);
    }
}
