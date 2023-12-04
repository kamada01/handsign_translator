package com.google.mediapipe.examples.handsign_translator;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

import java.util.List;

public class IntroViewPagerAdapter extends RecyclerView.Adapter<IntroViewPagerAdapter.ViewHolder> {

    Context mContext;
    List<ScreenItem> mListScreen;

    public IntroViewPagerAdapter(Context mContext, List<ScreenItem> mListScreen) {
        this.mContext = mContext;
        this.mListScreen = mListScreen;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View layoutScreen = inflater.inflate(R.layout.layout_screen, parent, false);
        return new ViewHolder(layoutScreen);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScreenItem screenItem = mListScreen.get(position);

        holder.title.setText(screenItem.getTitle());
        holder.desc.setText(screenItem.getDescription());
        holder.imgSlide.setImageResource(screenItem.getScreenImg());
    }

    @Override
    public int getItemCount() {
        return mListScreen.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgSlide;
        TextView title;
        TextView desc;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgSlide = itemView.findViewById(R.id.intro_img);
            title = itemView.findViewById(R.id.introTitle);
            desc = itemView.findViewById(R.id.introDesc);
        }
    }
}

