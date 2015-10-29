package edu.uvawise.iris.edu.uvawise.iris.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import edu.uvawise.iris.R;


public class EmailListViewAdapter extends ArrayAdapter<MimeMessage>{

    final static int LAYOUT_ID = R.layout.list_email_item;

    private EmailListViewAdapter(Context context) {
        super(context, LAYOUT_ID);
    }

    public EmailListViewAdapter(Context context,List<MimeMessage> items) {
        super(context, LAYOUT_ID, items);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View v = convertView;

        if (v == null) {
            LayoutInflater vi;
            vi = LayoutInflater.from(getContext());
            v = vi.inflate(LAYOUT_ID, null);
        }
        Calendar myCal = new GregorianCalendar();

        MimeMessage msg = getItem(position);

        if (msg != null){
            TextView fromTextView = (TextView) v.findViewById(R.id.fromTextView);
            TextView subjectTextView = (TextView) v.findViewById(R.id.subjectTextview);
            TextView dateTextView = (TextView) v.findViewById(R.id.dateTextView);

            try {
                if (fromTextView != null) fromTextView.setText(msg.getFrom()[0].toString());
                if (subjectTextView != null) subjectTextView.setText(msg.getSubject());
                if (dateTextView != null) {
                    DateFormat formatter = new SimpleDateFormat("MM/dd/yy hh:mm a");
                    dateTextView.setText(formatter.format(msg.getSentDate()));
                }
            } catch (Exception e) {
                Toast.makeText(getContext(),"Error displaying emails.", Toast.LENGTH_SHORT).show();
                Log.e("ERROR","Email layout could not display correctly. - "+e.getMessage());
            }
        }
                return v;
    }


}
