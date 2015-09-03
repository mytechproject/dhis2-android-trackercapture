/*
 * Copyright (c) 2015, dhis2
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.hisp.dhis.android.trackercapture.fragments.enrollment;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.raizlabs.android.dbflow.structure.Model;
import com.squareup.otto.Subscribe;

import org.hisp.dhis.android.sdk.R;
import org.hisp.dhis.android.sdk.controllers.DhisService;
import org.hisp.dhis.android.sdk.controllers.metadata.MetaDataController;
import org.hisp.dhis.android.sdk.persistence.loaders.DbLoader;
import org.hisp.dhis.android.sdk.persistence.models.ProgramTrackedEntityAttribute;
import org.hisp.dhis.android.sdk.persistence.models.TrackedEntityAttributeValue;
import org.hisp.dhis.android.sdk.ui.fragments.dataentry.DataEntryFragment;
import org.hisp.dhis.android.sdk.ui.fragments.dataentry.HideLoadingDialogEvent;
import org.hisp.dhis.android.sdk.ui.fragments.dataentry.RefreshListViewEvent;
import org.hisp.dhis.android.sdk.ui.fragments.dataentry.RowValueChangedEvent;
import org.hisp.dhis.android.sdk.ui.fragments.dataentry.SaveThread;
import org.hisp.dhis.android.sdk.utils.support.DateUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class EnrollmentDataEntryFragment extends DataEntryFragment<EnrollmentDataEntryFragmentForm> implements DatePickerDialog.OnDateSetListener, View.OnClickListener
{
    public static final String TAG = EnrollmentDataEntryFragment.class.getSimpleName();
    private static final String EMPTY_FIELD = "";
    private static final String ORG_UNIT_ID = "extra:orgUnitId";
    private static final String PROGRAM_ID = "extra:ProgramId";
    private static final String TRACKEDENTITYINSTANCE_ID = "extra:TrackedEntityInstanceId";
    private EnrollmentDataEntryFragmentForm form;
    private View enrollmentDatePicker;
    private View incidentDatePicker;
    private SaveThread saveThread;

    public static EnrollmentDataEntryFragment newInstance(String unitId, String programId) {
        EnrollmentDataEntryFragment fragment = new EnrollmentDataEntryFragment();
        Bundle args = new Bundle();
        args.putString(ORG_UNIT_ID, unitId);
        args.putString(PROGRAM_ID, programId);
        fragment.setArguments(args);
        return fragment;
    }

    public static EnrollmentDataEntryFragment newInstance(String unitId, String programId, long trackedEntityInstanceId) {
        EnrollmentDataEntryFragment fragment = new EnrollmentDataEntryFragment();
        Bundle args = new Bundle();
        args.putString(ORG_UNIT_ID, unitId);
        args.putString(PROGRAM_ID, programId);
        args.putLong(TRACKEDENTITYINSTANCE_ID, trackedEntityInstanceId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(saveThread == null || saveThread.isKilled()) {
            saveThread = new SaveThread();
            saveThread.start();
        }
        saveThread.init(this);
    }

    @Override
    public void onDestroy() {
        saveThread.kill();
        super.onDestroy();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        enrollmentDatePicker = LayoutInflater.from(getActivity())
                .inflate(R.layout.fragment_data_entry_date_picker, listView, false);
        incidentDatePicker = LayoutInflater.from(getActivity())
                .inflate(R.layout.fragment_data_entry_date_picker, listView, false);
        listView.addHeaderView(enrollmentDatePicker);
        listView.addHeaderView(incidentDatePicker);
    }

    @Override
    public Loader<EnrollmentDataEntryFragmentForm> onCreateLoader(int id, Bundle args) {
        if (LOADER_ID == id && isAdded()) {
            // Adding Tables for tracking here is dangerous (since MetaData updates in background
            // can trigger reload of values from db which will reset all fields).
            // Hence, it would be more safe not to track any changes in any tables
            List<Class<? extends Model>> modelsToTrack = new ArrayList<>();
            Bundle fragmentArguments = args.getBundle(EXTRA_ARGUMENTS);
            String orgUnitId = fragmentArguments.getString(ORG_UNIT_ID);
            String programId = fragmentArguments.getString(PROGRAM_ID);
            long trackedEntityInstance = fragmentArguments.getLong(TRACKEDENTITYINSTANCE_ID, -1);

            return new DbLoader<>(
                    getActivity().getBaseContext(), modelsToTrack, new EnrollmentDataEntryFragmentQuery(
                    orgUnitId,programId, trackedEntityInstance )
            );
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<EnrollmentDataEntryFragmentForm> loader, EnrollmentDataEntryFragmentForm data) {
        if (loader.getId() == LOADER_ID && isAdded()) {
            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);

            form = data;

            if (data.getProgram() != null) {
                attachEnrollmentDatePicker();
                attachIncidentDatePicker();
            }

            if(data.getDataEntryRows() != null && !data.getDataEntryRows().isEmpty())
            {
                listViewAdapter.swapData(data.getDataEntryRows());
            }
        }
    }

    private void attachEnrollmentDatePicker() {
        if (form != null && isAdded()) {
            final EditText datePickerEditText = (EditText) enrollmentDatePicker
                    .findViewById(R.id.date_picker_edit_text);
            View.OnClickListener onClearDateListener = new View.OnClickListener() {
                @Override public void onClick(View v) {
                    datePickerEditText.setText(EMPTY_FIELD);
                    form.getEnrollment().setDateOfEnrollment(EMPTY_FIELD);
                }
            };
            String reportDateDescription = form.getProgram().getDateOfEnrollmentDescription()== null ?
                    getString(R.string.report_date) : form.getProgram().getDateOfEnrollmentDescription();
            String value = null;
            if (form.getEnrollment() != null && form.getEnrollment().getDateOfEnrollment() != null) {
                DateTime date = DateTime.parse(form.getEnrollment().getDateOfEnrollment());
                String newValue = date.toString(DateUtils.DATE_PATTERN);
                datePickerEditText.setText(newValue);
            }
            setDatePicker(enrollmentDatePicker, reportDateDescription, value, onClearDateListener);
        }
    }

    private void attachIncidentDatePicker() {
        if (form != null && isAdded()) {
            final EditText datePickerEditText = (EditText) incidentDatePicker
                    .findViewById(R.id.date_picker_edit_text);
            View.OnClickListener onClearDateListener = new View.OnClickListener() {
                @Override public void onClick(View v) {
                    datePickerEditText.setText(EMPTY_FIELD);
                    form.getEnrollment().setDateOfIncident(EMPTY_FIELD);
                }
            };
            String reportDateDescription = form.getProgram().getDateOfIncidentDescription()== null ?
                    getString(R.string.report_date) : form.getProgram().getDateOfIncidentDescription();
            String value = null;
            if (form.getEnrollment() != null && form.getEnrollment().getDateOfIncident() != null) {
                DateTime date = DateTime.parse(form.getEnrollment().getDateOfIncident());
                value = date.toString(DateUtils.DATE_PATTERN);
            }
            setDatePicker(incidentDatePicker, reportDateDescription, value, onClearDateListener);
        }
    }

    private void setDatePicker(View datePicker, String labelValue, String dateValue, View.OnClickListener clearDateListener) {
        final TextView label = (TextView) datePicker
                .findViewById(R.id.text_label);
        final EditText datePickerEditText = (EditText) datePicker
                .findViewById(R.id.date_picker_edit_text);
        final ImageButton clearDateButton = (ImageButton) datePicker
                .findViewById(R.id.clear_edit_text);
        clearDateButton.setOnClickListener(clearDateListener);
        datePickerEditText.setOnClickListener(this);
        label.setText(labelValue);
        if(dateValue!=null) {
            datePickerEditText.setText(dateValue);
        }
    }

    @Override
    public void onLoaderReset(Loader<EnrollmentDataEntryFragmentForm> loader) {
        if (loader.getId() == LOADER_ID) {
            if (listViewAdapter != null) {
                listViewAdapter.swapData(null);
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
    }

    @Override
    protected ArrayList<String> getValidationErrors() {
        ArrayList<String> errors = new ArrayList<>();

        if (form.getEnrollment() == null || form.getProgram() == null || form.getOrganisationUnit() == null) {
            return errors;
        }

        if (isEmpty(form.getEnrollment().getDateOfEnrollment())) {
            String dateOfEnrollmentDescription = form.getProgram().getDateOfEnrollmentDescription() == null ?
                    getString(R.string.report_date) : form.getProgram().getDateOfEnrollmentDescription();
            errors.add(dateOfEnrollmentDescription);
        }

        Map<String, ProgramTrackedEntityAttribute> dataElements = toMap(
                MetaDataController.getProgramTrackedEntityAttributes(form.getProgram().getUid())
        );

        for (TrackedEntityAttributeValue value : form.getEnrollment().getAttributes()) {
            ProgramTrackedEntityAttribute programTrackedEntityAttribute = dataElements.get(value.getTrackedEntityAttributeId());

            if (programTrackedEntityAttribute.getMandatory() && isEmpty(value.getValue())) {
                errors.add(programTrackedEntityAttribute.getTrackedEntityAttribute().getName());
            }
        }
        return errors;
    }

    @Override
    public boolean isValid() {
        if (form.getEnrollment() == null || form.getProgram() == null || form.getOrganisationUnit() == null) {
            return false;
        }

        if (isEmpty(form.getEnrollment().getDateOfEnrollment())) {
            return false;
        }

        Map<String, ProgramTrackedEntityAttribute> dataElements = toMap(
                MetaDataController.getProgramTrackedEntityAttributes(form.getProgram().getUid())
        );

        for (TrackedEntityAttributeValue value : form.getEnrollment().getAttributes()) {
            ProgramTrackedEntityAttribute programTrackedEntityAttribute = dataElements.get(value.getTrackedEntityAttributeId());
            if (programTrackedEntityAttribute.getMandatory() && isEmpty(value.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, ProgramTrackedEntityAttribute> toMap(List<ProgramTrackedEntityAttribute> attributes) {
        Map<String, ProgramTrackedEntityAttribute> attributeMap = new HashMap<>();
        if (attributes != null && !attributes.isEmpty()) {
            for (ProgramTrackedEntityAttribute attribute : attributes) {
                attributeMap.put(attribute.getTrackedEntityAttributeId(), attribute);
            }
        }
        return attributeMap;
    }

    /**
     * returns true if the enrollment was successfully saved
     * @return
     */
    protected void save() {
        if (form != null && form.getTrackedEntityInstance() != null) {
            if (form.getTrackedEntityInstance().getLocalId() < 0) {
                //saving tei first to get auto-increment reference for enrollment
                form.getTrackedEntityInstance().setFromServer(false);
                form.getTrackedEntityInstance().save();
            }
            form.getEnrollment().setLocalTrackedEntityInstanceId(form.getTrackedEntityInstance().getLocalId());
            form.getEnrollment().setFromServer(false);
            form.getEnrollment().save();
            flagDataChanged(false);
        }
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        LocalDate date = new LocalDate(year, monthOfYear + 1, dayOfMonth);
        String newValue = date.toString(DateUtils.DATE_PATTERN);
        ((TextView) view.findViewById(R.id.date_picker_edit_text)).setText(newValue);
        form.getEnrollment().setDateOfEnrollment(newValue);
        onRowValueChanged(null);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.date_picker_edit_text:{
                    LocalDate currentDate = new LocalDate();
                DatePickerDialog picker = new DatePickerDialog(getActivity(),
                        EnrollmentDataEntryFragment.this, currentDate.getYear(),
                        currentDate.getMonthOfYear() - 1,
                        currentDate.getDayOfMonth());
                picker.getDatePicker().setMaxDate(DateTime.now().getMillis());
                picker.show();
                }
        }
    }

    @Subscribe
    public void onRowValueChanged(final RowValueChangedEvent event) {
        super.onRowValueChanged(event);
        saveThread.schedule();
    }

    @Subscribe
    public void onRefreshListView(RefreshListViewEvent event) {
        super.onRefreshListView(event);
    }

    @Subscribe
    public void onHideLoadingDialog(HideLoadingDialogEvent event) {
        super.onHideLoadingDialog(event);
    }
}
