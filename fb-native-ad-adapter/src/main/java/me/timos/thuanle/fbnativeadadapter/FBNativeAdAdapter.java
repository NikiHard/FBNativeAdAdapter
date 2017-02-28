package me.timos.thuanle.fbnativeadadapter;

import android.content.Context;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.facebook.ads.AbstractAdListener;
import com.facebook.ads.Ad;
import com.facebook.ads.AdChoicesView;
import com.facebook.ads.AdError;
import com.facebook.ads.MediaView;
import com.facebook.ads.NativeAd;
import com.rockerhieu.rvadapter.RecyclerViewAdapterWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by thuanle on 2/12/17.
 */
public class FBNativeAdAdapter extends RecyclerViewAdapterWrapper {

    public static final int TYPE_FB_NATIVE_ADS = 900;
    public static final int DEFAULT_AD_ITEM_INTERVAL = 10;

    private final Param mParam;

    private FBNativeAdAdapter(Param param) {
        super(param.adapter);
        this.mParam = param;

        assertConfig();
        setSpanAds();
    }

    private void assertConfig() {
        if (mParam.gridLayoutManager != null) {
            //if user set span ads
            int nCol = mParam.gridLayoutManager.getSpanCount();
            if (mParam.adItemInterval % nCol != 0) {
                throw new IllegalArgumentException(String.format("The adItemInterval (%d) is not divisible by number of columns in GridLayoutManager (%d)", mParam.adItemInterval, nCol));
            }
        }
    }

    private int convertAdPosition2OrgPosition(int position) {
        return position - (position + 1) / (mParam.adItemInterval + 1);
    }

    @Override
    public int getItemCount() {
        int realCount = super.getItemCount();
        return realCount + realCount / mParam.adItemInterval;
    }

    @Override
    public int getItemViewType(int position) {
        if (isAdPosition(position)) {
            return TYPE_FB_NATIVE_ADS;
        }
        return super.getItemViewType(convertAdPosition2OrgPosition(position));
    }

    private boolean isAdPosition(int position) {
        return (position + 1) % (mParam.adItemInterval + 1) == 0;
    }

    public void onBindAdViewHolder(final RecyclerView.ViewHolder holder) {
        final AdViewHolder adHolder = (AdViewHolder) holder;
        if (mParam.forceReloadAdOnBind || !adHolder.loaded) {
            final NativeAd nativeAd = new NativeAd(adHolder.getContext(), mParam.facebookPlacementId);
            nativeAd.setAdListener(new AbstractAdListener() {
                @Override
                public void onAdLoaded(Ad ad) {
                    if (ad != nativeAd) {
                        return;
                    }
                    adHolder.nativeAdContainer.setVisibility(View.VISIBLE);


                    // Set the Text.
                    adHolder.nativeAdTitle.setText(nativeAd.getAdTitle());
                    adHolder.nativeAdSocialContext.setText(nativeAd.getAdSocialContext());
                    adHolder.nativeAdBody.setText(nativeAd.getAdBody());
                    adHolder.nativeAdCallToAction.setText(nativeAd.getAdCallToAction());

                    // Download and display the ad icon.
                    NativeAd.Image adIcon = nativeAd.getAdIcon();
                    NativeAd.downloadAndDisplayImage(adIcon, adHolder.nativeAdIcon);

                    // Download and display the cover image.
                    adHolder.nativeAdMedia.setNativeAd(nativeAd);

                    // Add the AdChoices icon
                    AdChoicesView adChoicesView = new AdChoicesView(adHolder.getContext(), nativeAd, true);
                    adHolder.adChoicesContainer.removeAllViews();
                    adHolder.adChoicesContainer.addView(adChoicesView);

                    // Register the Title and CTA button to listen for clicks.
                    List<View> clickableViews = new ArrayList<>();
                    clickableViews.add(adHolder.nativeAdTitle);
                    clickableViews.add(adHolder.nativeAdCallToAction);
                    nativeAd.registerViewForInteraction(adHolder.nativeAdContainer, clickableViews);

                    adHolder.loaded = true;
                }

                @Override
                public void onError(Ad ad, AdError adError) {
                    adHolder.nativeAdContainer.setVisibility(View.GONE);
                }
            });
            nativeAd.loadAd();
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_FB_NATIVE_ADS) {
            onBindAdViewHolder(holder);
        } else {
            super.onBindViewHolder(holder, convertAdPosition2OrgPosition(position));
        }
    }

    public RecyclerView.ViewHolder onCreateAdViewHolder(ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View adLayoutOutline = inflater
                .inflate(mParam.itemContainerLayoutRes, parent, false);
        ViewGroup vg = (ViewGroup) adLayoutOutline.findViewById(mParam.itemContainerId);

        LinearLayout adLayoutContent = (LinearLayout) inflater
                .inflate(R.layout.item_facebook_native_ad, parent, false);
        vg.addView(adLayoutContent);
        return new AdViewHolder(adLayoutOutline);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_FB_NATIVE_ADS) {
            return onCreateAdViewHolder(parent);
        }
        return super.onCreateViewHolder(parent, viewType);
    }

    private void setSpanAds() {
        if (mParam.gridLayoutManager == null) {
            return ;
        }
        final GridLayoutManager.SpanSizeLookup spl = mParam.gridLayoutManager.getSpanSizeLookup();
        mParam.gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (isAdPosition(position)){
                    return spl.getSpanSize(position);
                }
                return 1;
            }
        });
    }

    private static class Param {
        String facebookPlacementId;
        RecyclerView.Adapter adapter;
        int adItemInterval;
        boolean forceReloadAdOnBind;

        @LayoutRes
        int itemContainerLayoutRes;

        @IdRes
        int itemContainerId;

        GridLayoutManager gridLayoutManager;
    }

    public static class Builder {
        private final Param mParam;

        private Builder(Param param) {
            mParam = param;
        }

        public static Builder with(String placementId, RecyclerView.Adapter wrapped) {
            Param param = new Param();
            param.facebookPlacementId = placementId;
            param.adapter = wrapped;

            //default value
            param.adItemInterval = DEFAULT_AD_ITEM_INTERVAL;
            param.itemContainerLayoutRes = R.layout.item_facebook_native_ad_outline;
            param.itemContainerId = R.id.ad_container;
            param.forceReloadAdOnBind = true;
            return new Builder(param);
        }

        public Builder adItemIterval(int interval) {
            mParam.adItemInterval = interval;
            return this;
        }

        public Builder adLayout(@LayoutRes int layoutContainerRes, @IdRes int itemContainerId) {
            mParam.itemContainerLayoutRes = layoutContainerRes;
            mParam.itemContainerId = itemContainerId;
            return this;
        }

        public FBNativeAdAdapter build() {
            return new FBNativeAdAdapter(mParam);
        }

        public Builder enableSpanRow(GridLayoutManager layoutManager) {
            mParam.gridLayoutManager = layoutManager;
            return this;
        }

        public Builder forceReloadAdOnBind(boolean forced) {
            mParam.forceReloadAdOnBind = forced;
            return this;
        }
    }

    private static class AdViewHolder extends RecyclerView.ViewHolder {
        ImageView nativeAdIcon;
        TextView nativeAdTitle;
        MediaView nativeAdMedia;
        TextView nativeAdSocialContext;
        TextView nativeAdBody;
        Button nativeAdCallToAction;
        LinearLayout adChoicesContainer;
        LinearLayout nativeAdContainer;
        boolean loaded;

        AdViewHolder(View view) {
            super(view);
            nativeAdContainer = (LinearLayout) view.findViewById(R.id.fb_native_ad_container);
            nativeAdIcon = (ImageView) view.findViewById(R.id.native_ad_icon);
            nativeAdTitle = (TextView) view.findViewById(R.id.native_ad_title);
            nativeAdMedia = (MediaView) view.findViewById(R.id.native_ad_media);
            nativeAdSocialContext = (TextView) view.findViewById(R.id.native_ad_social_context);
            nativeAdBody = (TextView) view.findViewById(R.id.native_ad_body);
            nativeAdCallToAction = (Button) view.findViewById(R.id.native_ad_call_to_action);
            adChoicesContainer = (LinearLayout) view.findViewById(R.id.ad_choices_container);
            loaded = false;
        }

        public Context getContext() {
            return nativeAdContainer.getContext();
        }
    }
}
