package edu.uic.cs440;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class Preferences extends PreferenceActivity{
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		// Reset Config State Clicked
		Preference configState = (Preference) findPreference("camomatic_config_state");
		configState.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			public boolean onPreferenceClick(Preference preference) {
				Toast.makeText(getBaseContext(), "Config State reset. Please restart the app",
							   Toast.LENGTH_LONG).show();
				
				SharedPreferences prefs = PreferenceManager
										.getDefaultSharedPreferences(getBaseContext());
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString("camomatic_config_state", "false");
				editor.commit();
				return true;
			}
			
		});
	}

}
