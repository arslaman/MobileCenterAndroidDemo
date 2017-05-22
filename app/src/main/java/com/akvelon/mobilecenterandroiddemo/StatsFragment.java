package com.akvelon.mobilecenterandroiddemo;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;

import com.akvelon.mobilecenterandroiddemo.helpers.DateHelper;
import com.akvelon.mobilecenterandroiddemo.services.Fitness.FitnessData;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.microsoft.azure.mobile.crashes.Crashes;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link StatsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class StatsFragment extends Fragment implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {

    public static final String TAG = "StepCounter";

    private LineChart mChart;
    private RadioGroup mRadioGroup;
    private Context mContext;
    private MyFitnessTask mFitnessTask;
    private List<FitnessData> mFitnessDataList;
    private Date mFitnessLastUpdatedDate;
    private FitnessDataType mSelectedFitnessType = FitnessDataType.STEPS;

    private enum FitnessDataType {
        STEPS,
        CALORIES,
        DISTANCE,
        ACTIVE_TIME
    }

    public StatsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment StatsFragment.
     */
    public static StatsFragment newInstance() {
        StatsFragment fragment = new StatsFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_stats, container, false);

        mRadioGroup = (RadioGroup) view.findViewById(R.id.statistics_radio_buttons);
        mRadioGroup.setOnCheckedChangeListener(this);

        Button crashButton = (Button) view.findViewById(R.id.stats_crash_button);
        crashButton.setOnClickListener(this);

        mChart = (LineChart) view.findViewById(R.id.statistics_chart);
        initChart();
        updateChartData();

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onStart() {
        super.onStart();

        final int MS_IN_MINUTE = 60 * 1000;
        long outdatedTimeout = 1 * MS_IN_MINUTE;
        boolean dataOutdated = mFitnessLastUpdatedDate == null || mFitnessLastUpdatedDate.getTime() < new Date().getTime() - outdatedTimeout;
        boolean asyncTaskNotRunning = mFitnessTask == null || mFitnessTask.getStatus() == AsyncTask.Status.FINISHED;
        if (dataOutdated && asyncTaskNotRunning) {
            mFitnessTask = new MyFitnessTask(mContext);
            mFitnessTask.execute();
        }
    }

    private void initChart() {
        // disable touch gestures
        mChart.setTouchEnabled(false);
        mChart.setDragEnabled(false);
        mChart.setScaleXEnabled(false);
        mChart.setScaleYEnabled(false);
        mChart.getDescription().setEnabled(false);
        mChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);

        mChart.getAxisLeft().setDrawGridLines(false);
        mChart.getAxisLeft().setLabelCount(5, false);

        mChart.getAxisRight().setEnabled(false);

        mChart.setDrawBorders(false);
        mChart.getLegend().setEnabled(false);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.stats_crash_button:
                // track crash click event
                ((MyApplication)mContext.getApplicationContext()).getAnalyticsService().trackCrashClick();
                // do manual crash
                //https://docs.microsoft.com/en-us/mobile-center/sdk/crashes/android
                Crashes.generateTestCrash();

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Crash was generated")
                        .setMessage("Crashes API can only be used in debug builds and won't do anything in release builds.")
                        .setIcon(R.mipmap.ic_launcher)
                        .setCancelable(false)
                        .setNegativeButton("OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });
                AlertDialog alert = builder.create();
                alert.show();
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        switch (checkedId) {
            case R.id.stats_radio_steps:
                mSelectedFitnessType = FitnessDataType.STEPS;
                break;
            case R.id.stats_radio_calories:
                mSelectedFitnessType = FitnessDataType.CALORIES;
                break;
            case R.id.stats_radio_distance:
                mSelectedFitnessType = FitnessDataType.DISTANCE;
                break;
            case R.id.stats_radio_active_time:
                mSelectedFitnessType = FitnessDataType.ACTIVE_TIME;
                break;
        }
        updateChartData();
    }

    private void updateChartData() {
        if (mFitnessDataList == null || mFitnessDataList.size() == 0) {
            return;
        }

        // first timestamp in our data set, other timestamps will be relative to this
        // see https://github.com/PhilJay/MPAndroidChart/issues/789#issuecomment-241507904
        long referenceTimestamp = mFitnessDataList.get(0).getDate().getTime();

        final long MS_IN_DAY = 86400000;
        long widthValue = mFitnessDataList.get(mFitnessDataList.size() - 1).getDate().getTime() - referenceTimestamp;
        long widthScale = widthValue + MS_IN_DAY;
        List<Entry> values = getEntryValues(mFitnessDataList, mSelectedFitnessType, referenceTimestamp, widthScale, widthValue);

        // create a data with values
        LineDataSet dataSet = lineDataSet(values);
        // create a data object with the data set
        LineData data = lineData(dataSet);

        // configure x axis to show dates
        // see https://github.com/PhilJay/MPAndroidChart/issues/789#issuecomment-241507904
        configureXAxisFormatter(referenceTimestamp);
        mChart.getXAxis().setLabelCount(values.size());

        mChart.getXAxis().setAxisMinimum(values.get(0).getX());
        mChart.getXAxis().setAxisMaximum(widthScale);
        // do not show negative values
        mChart.getAxisLeft().setAxisMinimum(0f);

        // set data
        mChart.setData(data);
        mChart.invalidate();
    }

    private List<Entry> getEntryValues(List<FitnessData> fitnessDataList, FitnessDataType fitnessType, long referenceTimestamp, float widthScale, float widthValue) {
        ArrayList<Entry> values = new ArrayList<Entry>();
        for (FitnessData fitnessData : fitnessDataList) {
            float x = ((float)(fitnessData.getDate().getTime() - referenceTimestamp) / widthValue) * widthScale;
            float y = (float)getAppropriateFitnessValue(fitnessData, fitnessType);
            values.add(new Entry(x, y));
        }
        return values;
    }

    private void configureXAxisFormatter(long referenceTimestamp) {
        ChartDateAxisValueFormatter xAxisFormatter = new ChartDateAxisValueFormatter(referenceTimestamp);
        XAxis xAxis = mChart.getXAxis();
        xAxis.setValueFormatter(xAxisFormatter);
    }

    private LineDataSet lineDataSet(List<Entry> values) {
        // create a data set and give it a type
        LineDataSet set = new LineDataSet(values, "DataSet");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setValueTextColor(ColorTemplate.getHoloBlue());
        set.setLineWidth(1.5f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setDrawFilled(true);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setDrawCircleHole(false);

        return set;
    }

    private LineData lineData(LineDataSet dataSet) {
        // create a data object with the datasets
        LineData data = new LineData(dataSet);
        data.setValueTextColor(Color.WHITE);
        data.setValueTextSize(9f);

        return data;
    }

    private double getAppropriateFitnessValue(FitnessData fitnessData, FitnessDataType neededType) {
        switch (neededType) {
            case STEPS:
                return fitnessData.getSteps();
            case CALORIES:
                return fitnessData.getCalories();
            case DISTANCE:
                final int METERS_IN_KILOMETER = 1000;
                return fitnessData.getDistance() / METERS_IN_KILOMETER;
            default:
                final int MS_IN_HOUR = 1000 * 60 * 60;
                return (double)fitnessData.getActiveTime() / MS_IN_HOUR;
        }
    }

    class MyFitnessTask extends FitnessAsyncTask {

        public MyFitnessTask(Context context) {
            super(context);
        }

        @Override
        protected void updateUI(List<FitnessData> dataList) {
            // if dataList is not null, then result is success
            boolean success = dataList != null;

            // track Google Fit retrieve result event
            ((MyApplication)mContext.getApplicationContext()).getAnalyticsService().trackGoogleFitRetrieveResult(
                    success,
                    null
            );

            if (dataList != null && dataList.size() > 0) {
                mFitnessDataList = dataList;
                updateChartData();

                // update last update date to ensure outdating
                mFitnessLastUpdatedDate = new Date();
            }
        }

        @Override
        protected Date startDate() {
            int daysAgo = 4;
            return DateHelper.date(daysAgo);
        }

        @Override
        protected Date endDate() {
            return new Date();
        }
    }
}
