package com.beattheheat.beatthestreet;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.beattheheat.beatthestreet.Networking.OC_API.OCStop;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by Matt on 2017-10-27
 *
 * Takes data from OCStop objects and puts it into the RecyclerView layout
 */

class StopAdapter extends RecyclerView.Adapter<StopAdapter.StopViewHolder> {

    private Context context;
    private ArrayList<OCStop> stops;

     StopAdapter(Context context, ArrayList<OCStop> stopList) {
         this.context = context;
         this.stops = stopList;
         Collections.sort(stops);
    }

    @Override
    public StopViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Inflate the item Layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.stop_layout, parent, false);
        return new StopViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final StopViewHolder viewHolder , int position) {
        // Set the stop name and code
        viewHolder.stopName.setText(stops.get(position).getStopName());
        viewHolder.stopCode.setText("" + stops.get(position).getStopCode());

        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Get stopCode and pass it back to DisplayStopsActivity
                ((DisplayStopsActivity)context).onClick(viewHolder.stopCode.getText().toString());
            }
        });
    }

    @Override
    public int getItemCount() { return stops.size(); }

    /* Helper class that takes OCStop info and
       puts it into the layout at the appropriate position */
    static class StopViewHolder extends RecyclerView.ViewHolder {
        TextView stopName;
        TextView stopCode;

        StopViewHolder(View itemView) {
            super(itemView);
            stopName = (TextView)itemView.findViewById(R.id.stop_name);
            stopCode = (TextView)itemView.findViewById(R.id.stop_code);
        }
    }

    // Replaces the current list of stops with the ones that match the search
    void setFilter(ArrayList<OCStop> newList) {
        stops = new ArrayList<>();
        stops.addAll(newList);
        Collections.sort(stops);
        notifyDataSetChanged(); // Refresh the adapter
    }
}
