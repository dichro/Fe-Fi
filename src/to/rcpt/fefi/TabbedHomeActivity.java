package to.rcpt.fefi;

import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TabHost;

public class TabbedHomeActivity extends TabActivity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tabbed_home);
		
		TabHost tabHost = getTabHost();
		Resources res = getResources();
		TabHost.TabSpec tabSpec;
		Intent intent;
		
		intent = new Intent().setClass(this, EyefiCardListActivity.class);
		tabSpec = tabHost.newTabSpec("cards").setIndicator("Cards",
				res.getDrawable(android.R.drawable.ic_menu_save)).setContent(intent);
		tabHost.addTab(tabSpec);
		
		intent = new Intent().setClass(this, IncomingImagesActivity.class);
		tabSpec = tabHost.newTabSpec("pictures").setIndicator("Pictures", 
				res.getDrawable(android.R.drawable.ic_menu_gallery)).setContent(intent);
		tabHost.addTab(tabSpec);
		
		intent = new Intent().setClass(this, GeotagActivity.class);
		tabSpec = tabHost.newTabSpec("geotag").setIndicator("Geotag", 
				res.getDrawable(android.R.drawable.ic_menu_mapmode)).setContent(intent);
		tabHost.addTab(tabSpec);
		
		tabHost.setCurrentTab(0);
	}
}
