package com.suez.addons;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.odoo.R;
import com.odoo.core.account.About;
import com.odoo.core.support.OUser;
import com.odoo.core.support.addons.fragment.BaseFragment;
import com.odoo.core.support.drawer.ODrawerItem;
import com.odoo.core.utils.OPreferenceManager;
import com.odoo.core.utils.OResource;
import com.suez.SuezConstants;
import com.suez.addons.blending.MixBlendingMenusActivity;
import com.suez.addons.models.StockProductionLot;
import com.suez.addons.processing.PretreatmentActivity;
import com.suez.addons.processing.ProcessingTestActivity;
import com.suez.addons.scan.ScanZbarActivity;
import com.suez.addons.tank_truck.TankTruckActivity;
import com.suez.addons.wac_info.DebugSqlActivity;
import com.suez.utils.ToastUtil;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;

public class SuezFragment extends BaseFragment implements View.OnKeyListener{
    private static final String TAG = SuezFragment.class.getSimpleName();
    private static final int SCANNING_REQUEST_CODE = 1;
    private Long mExitTime;
    private OPreferenceManager pref;
    private NotificationManager notificationManager;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstance){
        super.onCreateView(inflater, container, savedInstance);
        this.setHasOptionsMenu(true);
        this.mExitTime = 0L;
        pref = new OPreferenceManager(getContext());
        notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        View view = inflater.inflate(R.layout.suez_fragment, container,false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onResume(){
        super.onResume();
        this.getFocus();
    }

    private void getFocus(){
        this.getView().setFocusable(true);
        this.getView().setFocusableInTouchMode(true);
        this.getView().requestFocus();
        this.getView().setOnKeyListener(this);
    }

    @Override
    public List<ODrawerItem> drawerMenus(Context context){
        List<ODrawerItem> menu = new ArrayList<>();
        menu.add(new ODrawerItem(TAG)
            .setTitle(OResource.string(context, R.string.suez_label_suez))
            .setInstance(new SuezFragment())
            .setIcon(R.drawable.ic_action_company));
        return menu;
    }

    @Override
    public Class<?> database(){
        return StockProductionLot.class;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent){
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode){
            case SCANNING_REQUEST_CODE:
                if (Activity.RESULT_OK == resultCode){
                    Bundle bundle = intent.getExtras();
                    ToastUtil.toastShow(bundle.getString(SuezConstants.RESULT), getActivity());
                }
                break;
            default:
                break;
        }
    }

    @OnClick({R.id.btnTankTruck, R.id.btnWacInfo, R.id.btnMixBlending, R.id.btnMoveWac, R.id.btnRepackaging, R.id.btnDirectBurn, R.id.btnPretreatment})
    public void onClick(View view){
        Intent intent;
        switch (view.getId()){
            case R.id.btnTankTruck:
                intent = new Intent(getActivity(), TankTruckActivity.class);
                this.startActivity(intent);
                break;
            case R.id.btnWacInfo:
                intent = new Intent(getActivity(), ScanZbarActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                intent.putExtra(SuezConstants.COMMON_KEY, SuezConstants.WAC_INFO_KEY);
                startActivity(intent);
                break;
            case R.id.btnMixBlending:
                intent = new Intent(getActivity(), MixBlendingMenusActivity.class);
                startActivity(intent);
                break;
            case R.id.btnMoveWac:
                intent = new Intent(getActivity(), ScanZbarActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                intent.putExtra(SuezConstants.COMMON_KEY, SuezConstants.WAC_MOVE_KEY);
                startActivity(intent);
                break;
            case R.id.btnRepackaging:
                intent = new Intent(getActivity(), ScanZbarActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                intent.putExtra(SuezConstants.COMMON_KEY, SuezConstants.REPACKING_KEY);
                startActivity(intent);
                break;
            case R.id.btnDirectBurn:
                intent = new Intent(getActivity(), ScanZbarActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                intent.putExtra(SuezConstants.COMMON_KEY, SuezConstants.DIRECT_BURN_KEY);
                startActivity(intent);
                break;
            case R.id.btnPretreatment:
                intent = new Intent(getActivity(), ScanZbarActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                intent.putExtra(SuezConstants.COMMON_KEY, SuezConstants.PRETREATMENT_KEY);
                startActivity(intent);
                break;
        }
    }

    @OnLongClick({R.id.btnMixBlending, R.id.btnTankTruck})
    public boolean onLongClick(View view) {
        switch (view.getId()) {
            case R.id.btnMixBlending:
                if (OUser.current(getContext()).getUsername().equals("admin") && pref.getBoolean(About.DEVELOPER_MODE, false)) {
                    Log.d(TAG, "Start Flush Testing");
                    Intent intent = new Intent(getContext(), ProcessingTestActivity.class);
                    startActivity(intent);
                }
                break;
            case R.id.btnTankTruck:
                if (pref.getBoolean(About.DEVELOPER_MODE, false)) {
                    Intent intent = new Intent(getContext(), DebugSqlActivity.class);
                    startActivity(intent);
                }
                break;
        }
        return true;
    }

    @Override
    public boolean onKey(View v, int i, KeyEvent event){
        if (event.getAction() == KeyEvent.ACTION_DOWN && i == KeyEvent.KEYCODE_BACK){
            if ((System.currentTimeMillis() - mExitTime) > SuezConstants.EXIT_TIME_GAP){
                Toast.makeText(getActivity(), R.string.exit_app, Toast.LENGTH_SHORT).show();
                mExitTime = System.currentTimeMillis();
            } else {
                getActivity().finish();
                notificationManager.cancelAll();
                System.exit(0);
            }
            return true;
        }
        return false;
    }
}