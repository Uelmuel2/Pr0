package com.pr0gramm.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.akodiakson.sdk.simple.Sdk;
import com.google.android.exoplayer.DecoderInfo;
import com.google.android.exoplayer.MediaCodecUtil;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.services.RecentSearchesServices;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.UserService;
import com.pr0gramm.app.services.preloading.PreloadManager;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.ui.dialogs.UpdateDialogFragment;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import okhttp3.OkHttpClient;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.util.async.Async;

import static com.google.common.base.Strings.emptyToNull;
import static com.pr0gramm.app.services.ThemeHelper.theme;
import static org.joda.time.Instant.now;

/**
 */
public class SettingsActivity extends BaseAppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(theme().basic);
        super.onCreate(savedInstanceState);

        String category = null;
        String action = getIntent().getAction();
        if (action != null && action.startsWith("preference://"))
            category = emptyToNull(action.substring("preference://".length()));

        if (savedInstanceState == null) {
            SettingsFragment fragment = new SettingsFragment();
            fragment.setArguments(AndroidUtility.bundle("category", category));

            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commit();
        }
    }

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Inject
        UserService userService;

        @Inject
        Settings settings;

        @Inject
        PreloadManager preloadManager;

        @Inject
        OkHttpClient okHttpClient;

        @Inject
        RecentSearchesServices recentSearchesServices;

        private Subscription preloadItemsSubscription;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Dagger.appComponent(getActivity()).inject(this);

            if (!userService.isAuthorized()) {
                // reset those content types - better be sure!
                Settings.resetContentTypeSettings(settings);
            }

            addPreferencesFromResource(R.xml.preferences);

            updateCodecsPreference("pref_video_codec", "video/avc");
            updateCodecsPreference("pref_audio_codec", "audio/mp4a-latm");

            String category = getArguments().getString("category");
            if (category != null) {
                Preference root = getPreferenceManager().findPreference(category);
                if (root != null) {
                    getActivity().setTitle(root.getTitle());
                    setPreferenceScreen((PreferenceScreen) root);
                }
            }

            if (!BuildConfig.DEBUG) {
                hideDebugPreferences();
            }
        }

        private void updateCodecsPreference(String prefName, String mimeType) {
            List<CharSequence> entries = new ArrayList<>();
            List<CharSequence> entryValues = new ArrayList<>();

            ListPreference pref = (ListPreference) findPreference(prefName);
            if (pref == null)
                return;

            if (Sdk.isAtLeastJellyBean()) {
                entries.add("Software");
                entryValues.add("software");

                entries.add("Hardware");
                entryValues.add("hardware");

                try {
                    List<DecoderInfo> codecs = MediaCodecUtil.getDecoderInfos(mimeType, false);
                    for (DecoderInfo codec : codecs) {
                        entries.add(codec.name.toLowerCase());
                        entryValues.add(codec.name);
                    }
                } catch (MediaCodecUtil.DecoderQueryException ignored) {
                }
            }

            if (entries.size() > 3) {
                pref.setDefaultValue("hardware");
                pref.setEntries(entries.toArray(new CharSequence[0]));
                pref.setEntryValues(entryValues.toArray(new CharSequence[0]));
            } else {
                pref.setEnabled(false);
            }
        }

        private void hideDebugPreferences() {
            Preference pref = findPreference("prefcat_debug");
            if (pref != null) {
                getPreferenceScreen().removePreference(pref);
            }
        }

        private void updatePreloadInfo() {
            Preference preference = getPreferenceManager().findPreference("pref_pseudo_clean_preloaded");
            if (preference != null) {
                final int pseudo_clean_preloaded_summary_with_size = R.string.pseudo_clean_preloaded_summary_with_size;
                preloadItemsSubscription = preloadManager.all()
                        .subscribeOn(BackgroundScheduler.instance())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(items -> {
                            long totalSize = 0;
                            for (PreloadManager.PreloadItem item : items) {
                                totalSize += item.media().length();
                                totalSize += item.thumbnail().length();
                            }

                            preference.setSummary(getString(pseudo_clean_preloaded_summary_with_size,
                                    totalSize / (1024.f * 1024.f)));
                        });
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen()
                    .getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);

            updatePreloadInfo();
        }

        @Override
        public void onPause() {
            getPreferenceScreen()
                    .getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);

            if (preloadItemsSubscription != null) {
                preloadItemsSubscription.unsubscribe();
                preloadItemsSubscription = null;
            }

            super.onPause();
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, @NonNull Preference preference) {
            String preferenceKey = preference.getKey();
            if ("pref_pseudo_update".equals(preferenceKey)) {
                BaseAppCompatActivity activity = (BaseAppCompatActivity) getActivity();
                UpdateDialogFragment.checkForUpdates(activity, true);
                return true;
            }

            if ("pref_pseudo_changelog".equals(preferenceKey)) {
                AppCompatActivity activity = (AppCompatActivity) getActivity();
                ChangeLogDialog dialog = new ChangeLogDialog();
                dialog.show(activity.getSupportFragmentManager(), null);
                return true;
            }

            if ("pref_pseudo_feedback".equals(preferenceKey)) {
                startActivity(new Intent(getActivity(), ContactActivity.class));
                return true;
            }

            if ("pref_pseudo_recommend".equals(preferenceKey)) {
                String text = "Probiere mal die offizielle pr0gramm App aus: https://app.pr0gramm.com/";
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, "pr0gramm app");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(intent, getString(R.string.share_using)));
            }

            if ("pref_pseudo_clean_preloaded".equals(preferenceKey)) {
                Async.start(() -> {
                    // remove all the files!
                    preloadManager.deleteBefore(now());
                    return null;
                }, BackgroundScheduler.instance());
            }

            if ("pref_pseudo_clear_tag_suggestions".equals(preferenceKey)) {
                recentSearchesServices.clearHistory();
            }

            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            Activity activity = getActivity();

            if ("pref_convert_gif_to_webm".equals(key)) {
                if (preferences.getBoolean("pref_convert_gif_to_webm", false)) {
                    DialogBuilder.start(activity)
                            .content(R.string.gif_as_webm_might_be_buggy)
                            .positive()
                            .show();
                }
            }

            if ("pref_theme".equals(key)) {
                // get the correct theme for the app!
                ThemeHelper.updateTheme(getActivity());

                final Intent intent = getActivity().getIntent();
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                TaskStackBuilder.create(getActivity())
                        .addNextIntentWithParentStack(intent)
                        .startActivities();
            }
        }
    }
}
