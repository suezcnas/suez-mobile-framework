/**
 * Odoo, Open Source Management Solution
 * Copyright (C) 2012-today Odoo SA (<http:www.odoo.com>)
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details
 * <p/>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http:www.gnu.org/licenses/>
 * <p/>
 * Created on 7/1/15 5:11 PM
 */
package odoo.controls;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;

import com.google.gson.JsonArray;
import com.odoo.App;
import com.odoo.BaseAbstractListener;
import com.odoo.R;
import com.odoo.core.orm.ODataRow;
import com.odoo.core.orm.OModel;
import com.odoo.core.orm.ServerDataHelper;
import com.odoo.core.orm.fields.OColumn;
import com.odoo.core.orm.fields.types.OSelection;
import com.odoo.core.rpc.helper.ODomain;
import com.odoo.core.rpc.helper.OdooFields;
import com.odoo.core.support.list.OListAdapter;
import com.odoo.core.utils.OControls;
import com.odoo.core.utils.OResource;
import com.suez.utils.RecordUtils;
import com.suez.utils.SearchRecordsOnlineUtils;
import com.suez.utils.StockLocationFlags;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class SearchableItemActivity extends ActionBarActivity implements AdapterView.OnItemSelectedListener,
        AdapterView.OnItemClickListener, TextWatcher, View.OnClickListener,
        OListAdapter.OnSearchChange, OListAdapter.RowFilterTextListener, IOnQuickRecordCreateListener {
    public static final String TAG = SearchableItemActivity.class.getSimpleName();

    private EditText edt_searchable_input;
    private ListView mList = null;
    private OListAdapter mAdapter;
    private List<Object> objects = new ArrayList<>();
    private int selected_position = -1;
    private Boolean mLiveSearch = false;
    private int resource_array_id = -1;
    private OModel mModel = null;
    private OModel mRelModel = null;
    private Integer mRowId = null;
    private LiveSearch mLiveDataLoader = null;
    private Bundle formData;
    private ODomain liveDomain = new ODomain();
    private boolean isNetwork;
    private boolean showSpinner = false;
    private Spinner locationSpinner;
    private StockLocationFlags locationFlags;
    private ArrayAdapter<String> locationAdapter;
    private List<String> locations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.base_control_searchable_layout);
        setResult(RESULT_CANCELED);
        edt_searchable_input = (EditText) findViewById(R.id.edt_searchable_input);
        edt_searchable_input.addTextChangedListener(this);
        locationSpinner = (Spinner) findViewById(R.id.locations);
        isNetwork = ((App) getApplication()).networkState;
        Bundle extra = getIntent().getExtras();
        if (extra != null) {
            if (extra.containsKey("resource_id")) {
                resource_array_id = extra.getInt("resource_id");
            }
            if (extra.containsKey(OColumn.ROW_ID)) {
                mRowId = extra.getInt(OColumn.ROW_ID);
            }
            if (extra.containsKey("model")) {
                mModel = OModel.get(this, extra.getString("model"), null);
            }
            if (extra.containsKey("live_search")) {
                mLiveSearch = extra.getBoolean("live_search");
            }
            if (extra.containsKey("selected_position")) {
                selected_position = extra.getInt("selected_position");
            }
            if (extra.containsKey("search_hint")) {
                edt_searchable_input.setHint("Search "
                        + extra.getString("search_hint"));
            }
            if (extra.containsKey("show_spinner")) {
                showSpinner = extra.getBoolean("show_spinner");
            }
            if (resource_array_id != -1) {
                String[] arrays = getResources().getStringArray(
                        resource_array_id);
                for (int i = 0; i < arrays.length; i++) {
                    ODataRow row = new ODataRow();
                    row.put(OColumn.ROW_ID, i);
                    row.put(mRelModel.getDefaultNameColumn(), arrays[i]);
                    objects.add(row);
                }
            } else {
                if (extra.containsKey("column_name")) {
                    OColumn mCol = mModel.getColumn(extra.getString("column_name"));
                    mRelModel = mModel.createInstance(mCol.getType());

                    if (mRelModel.getModelName().equals("stock.location") && showSpinner) {
                        locationSpinner.setVisibility(View.VISIBLE);
                        locationFlags = StockLocationFlags.getInstance();
                        locationFlags.setLocations(mRelModel.select(null, "is_quick_filter = ?", new String[]{"true"}, "name"));
                        locations = locationFlags.getLocations(this);
                        if (isNetwork) {
                            ODomain domain = new ODomain();
                            domain.add("is_quick_filter", "=", true);
                            SearchRecordsOnlineUtils utils = new SearchRecordsOnlineUtils(mRelModel, new OdooFields(mRelModel.getColumns()), domain, 1000, 0, "name").setListener(new BaseAbstractListener(){
                                @Override
                                public void OnSuccessful(List<ODataRow> listRow) {
                                    locationFlags.setLocations(listRow);
                                    locations = locationFlags.getLocations(SearchableItemActivity.this);
                                    locationAdapter.clear();
                                    locationAdapter.addAll(locations);
                                    locationAdapter.notifyDataSetChanged();
                                }
                            });
                            utils.searchRecordsOnServer();
                        }
                        locationAdapter = new ArrayAdapter(this, android.R.layout.simple_expandable_list_item_1, locations);
                        locationSpinner.setAdapter(locationAdapter);
                        locationSpinner.setOnItemSelectedListener(this);
                    }
                    if (mCol.hasDomainFilterColumn()) {
                        formData = extra.getBundle("form_data");
                        liveDomain = mCol.getDomainFilterParser(mModel).getRPCDomain(formData);
                    }

                    if (!mCol.getDomains().values().isEmpty()){
                        for (OColumn.ColumnDomain domain: mCol.getDomains().values()) {
                            if (domain.getConditionalOperator() != null) {
                                liveDomain.add(domain.getConditionalOperator());
                            } else {
                                liveDomain.add(domain.getColumn(), domain.getOperator(), domain.getValue());
                            }
                        }
                    }
                    // Add by Joseph 180521
                    if (!isNetwork) {
                        List<ODataRow> rows = OSelectionField.getRecordItems(mRelModel, mCol, formData);
                        if (mRelModel.getModelName().equals("blending.waste.category")) {
                            objects.addAll(RecordUtils.nameGet(rows,
                                    new String[]{"name", "name_local"}, ':'));
                        } else {
                            objects.addAll(rows);
                        }
                    } else {
                        SearchRecordsOnlineUtils utils = new SearchRecordsOnlineUtils(mRelModel, new OdooFields(mRelModel.getColumns()), liveDomain).setListener(new BaseAbstractListener(){
                            @Override
                            public void OnSuccessful(List<ODataRow> listRow) {
                                if (mRelModel.getModelName().equals("blending.waste.category")) {
                                    objects.addAll(RecordUtils.nameGet(listRow, new String[]{"name", "name_local"}, ':'));
                                } else {
                                    objects.addAll(listRow);
                                }
                                mAdapter.notifyDataSetChanged();
                            }
                        });
                        utils.searchRecordsOnServer();
                    }
                }
            }

            mList = (ListView) findViewById(R.id.searchable_items);
            mList.setOnItemClickListener(this);
            mAdapter = new OListAdapter(this,
                    android.R.layout.simple_expandable_list_item_1, objects) {
                @Override
                public View getView(int position, View convertView,
                                    ViewGroup parent) {
                    View v = convertView;
                    if (v == null)
                        v = getLayoutInflater().inflate(getResource(), parent,
                                false);
                    ODataRow row = (ODataRow) objects.get(position);
                    OControls.setText(v, android.R.id.text1,
                            row.getString(mRelModel.getDefaultNameColumn()));
                    if (row.contains(OColumn.ROW_ID)
                            && selected_position == row.getInt(OColumn.ROW_ID)) {
                        v.setBackgroundColor(getResources().getColor(
                                R.color.control_pressed));
                    } else {
                        v.setBackgroundColor(Color.TRANSPARENT);
                    }
                    return v;
                }
            };
            if (mLiveSearch) {
                mAdapter.setOnSearchChange(this);
            }
            mList.setAdapter(mAdapter);
            mAdapter.setRowFilterTextListener(this);
        } else {
            finish();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        ODataRow data = (ODataRow) objects.get(position);
        if (!data.contains(OColumn.ROW_ID)) {
            QuickCreateRecordProcess quickCreateRecordProcess = new QuickCreateRecordProcess(this);
            quickCreateRecordProcess.execute(data);
        } else {
            onRecordCreated(data);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        edt_searchable_input.setText(locations.get(position));
        if (position == locations.size() - 1) {
            if (isNetwork) {
                ODomain domain = new ODomain();
                for (String flag: locations) {
                    domain.add("name", "not ilike", flag + "%");
                }
                domain.add("usage", "=", "internal");
                SearchRecordsOnlineUtils utils = new SearchRecordsOnlineUtils(mRelModel, new OdooFields(mRelModel.getColumns()), domain)
                        .setListener(new BaseAbstractListener(){
                            @Override
                            public void OnSuccessful(List<ODataRow> listRow) {
                                objects.clear();
                                objects.addAll(listRow);
                                mAdapter.notifyDataSetChanged();
                            }
                        });
                utils.searchRecordsOnServer();
            } else {
                StringBuilder queryBuilder = new StringBuilder("usage = ? and ");
                List<String> params = new ArrayList<>();
                params.add("internal");
                for (String flag: locations) {
                    if (locations.indexOf(flag) != locations.size() - 1) {
                        queryBuilder.append("name not like ? and ");
                        params.add(flag + '%');
                    }
                }
                queryBuilder.delete(queryBuilder.length() - 4, queryBuilder.length() - 1);
                List<ODataRow> res = mRelModel.select(null, queryBuilder.toString(), params.toArray(new String[params.size()]));
                objects.clear();
                objects.addAll(res);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onRecordCreated(ODataRow row) {
        Bundle extra = getIntent().getExtras();
        Intent intent = new Intent("searchable_value_select");
        if (extra.containsKey("column_name"))
            intent.putExtra("column_name", extra.getString("column_name"));
        intent.putExtra("selected_position", row.getInt(OColumn.ROW_ID));
        if (mRowId != null) {
            intent.putExtra("record_id", true);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        finish();
    }

    @Override
    public String filterCompareWith(Object object) {
        if (object instanceof ODataRow) {
            return ((ODataRow) object).getString("name");
        }
        return object.toString();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
                                  int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (!s.toString().equals(OResource.string(this, R.string.label_others))) {
            mAdapter.getFilter().filter(s);
        }
        ImageView imgView = (ImageView) findViewById(R.id.search_icon);
        if (s.length() > 0) {
            imgView.setImageResource(R.drawable.ic_action_navigation_close);
            imgView.setOnClickListener(this);
            imgView.setClickable(true);
        } else {
            imgView.setClickable(false);
            imgView.setImageResource(R.drawable.ic_action_search);
            imgView.setOnClickListener(null);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

    @Override
    public void onClick(View v) {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onSearchChange(List<Object> newRecords) {
        if (newRecords.size() <= 0) {
            if (mLiveDataLoader != null)
                mLiveDataLoader.cancel(true);
            if (edt_searchable_input.getText().length() >= 1) {
                mLiveDataLoader = new LiveSearch();
                mLiveDataLoader.execute(edt_searchable_input.getText()
                        .toString());
            }
        }
    }


    private class LiveSearch extends AsyncTask<String, Void, List<ODataRow>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            findViewById(R.id.loading_progress).setVisibility(View.VISIBLE);
            mList.setVisibility(View.GONE);
        }

        @Override
        protected List<ODataRow> doInBackground(String... params) {
            try {
                ServerDataHelper helper = mRelModel.getServerDataHelper();
                ODomain domain = new ODomain();
                domain.add(mRelModel.getDefaultNameColumn(), "ilike", params[0]);
                domain.append(liveDomain);
                return helper.searchRecords(new OdooFields(mRelModel.getColumns()), domain, 100);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(List<ODataRow> result) {
            super.onPostExecute(result);
            findViewById(R.id.loading_progress).setVisibility(View.GONE);
            mList.setVisibility(View.VISIBLE);
            if (result != null && result.size() > 0) {
                objects.clear();
                objects.addAll(result);
                mAdapter.notifiyDataChange(objects);
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            findViewById(R.id.loading_progress).setVisibility(View.GONE);
            mList.setVisibility(View.VISIBLE);
        }
    }

    private class QuickCreateRecordProcess extends AsyncTask<ODataRow, Void, ODataRow> {

        private ProgressDialog progressDialog;
        IOnQuickRecordCreateListener mOnQuickRecordCreateListener = null;

        public QuickCreateRecordProcess(IOnQuickRecordCreateListener listener) {
            mOnQuickRecordCreateListener = listener;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(SearchableItemActivity.this);
            progressDialog.setTitle(R.string.title_please_wait);
            progressDialog.setMessage(OResource.string(SearchableItemActivity.this, R.string.title_working));
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected ODataRow doInBackground(ODataRow... params) {
            try {
                Thread.sleep(700);
                return mRelModel.quickCreateRecord(params[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(ODataRow data) {
            super.onPostExecute(data);
            if (data != null && mOnQuickRecordCreateListener != null) {
                mOnQuickRecordCreateListener.onRecordCreated(data);
            }
            progressDialog.dismiss();
        }
    }

}
