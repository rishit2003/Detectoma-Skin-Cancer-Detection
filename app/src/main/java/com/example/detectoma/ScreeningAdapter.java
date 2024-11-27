package com.example.detectoma;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;
import java.util.Map;

public class ScreeningAdapter extends RecyclerView.Adapter<ScreeningAdapter.ViewHolder> {
//    private List<Screening> screeningList;
    private List<Map<String, Object>> screeningList;
    private Context context;
    private String patientId; // Add this member variable

    public ScreeningAdapter(List<Map<String, Object>> screeningList, Context context, String patientId) {
        this.screeningList = screeningList;
        this.context = context;
        this.patientId = patientId;

        // Retrieve the current user's UID (patientId)
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            patientId = currentUser.getUid();
        } else {
            patientId = null;
            // Optionally, you can handle the case where the user is not logged in
        }
    }



    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_screening, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> screeningData = screeningList.get(position);

        // Safely access data with type checking and casting
        // Get timestamp
        String timestamp = screeningData.get("timestamp") != null ?
                screeningData.get("timestamp").toString() : "N/A";
        String temperatureDiff = screeningData.get("temperatureDiff") != null ?
                String.valueOf(screeningData.get("temperatureDiff")) : "N/A";
        String distanceSurface = screeningData.get("distanceSurface") != null ?
                String.valueOf(screeningData.get("distanceSurface")) : "N/A";
        String distanceArm = screeningData.get("distanceArm") != null ?
                String.valueOf(screeningData.get("distanceArm")) : "N/A";
//        Boolean asymmetry = screeningData.get("asymmetry") != null ?
//                (Boolean) screeningData.get("asymmetry") : null;

        // Set data to your views
        holder.screeningDate.setText(timestamp);
//        holder.temperature.setText("Temperature Difference: " + temperatureDiff + "°C");
//        holder.distances.setText("Distance Surface: " + distanceSurface + " cm" + "Distance Arm: " + distanceArm + " cm");

//        holder.asymmetryTextView.setText(asymmetry != null ? asymmetry.toString() : "N/A");

//        holder.temperature.setText("Temperature: " + screening.getTempDifference());
//        holder.distances.setText("Distance 1: " + screening.getDistance1() + " Distance 2: " + screening.getDistance2());
//
//        holder.itemView.setOnClickListener(v -> {
//            Intent intent = new Intent(context, resultsActivity.class);
//            intent.putExtra("FORMATTED_DATE", timestamp);
//            context.startActivity(intent);
//        });

        // Ensure patientId is not null before setting the click listener
        if (patientId != null) {
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, resultsActivity.class);
                intent.putExtra("FORMATTED_DATE", timestamp);
                intent.putExtra("UID", patientId); // Pass patientId to the intent
                context.startActivity(intent);
            });
        } else {
            // Handle the case where patientId is null
            holder.itemView.setOnClickListener(v -> {
                // Show an error message or redirect to login
                Toast.makeText(context, "User not logged in.", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public int getItemCount() {
        return screeningList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView screeningDate;
//        TextView temperature;
//        TextView distances;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            screeningDate = itemView.findViewById(R.id.screeningDate);
//            temperature = itemView.findViewById(R.id.temperature);
//            distances = itemView.findViewById(R.id.distances);
        }
    }
}
