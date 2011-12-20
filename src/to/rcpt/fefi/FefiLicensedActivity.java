package to.rcpt.fefi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

public class FefiLicensedActivity extends Activity {
	boolean checkBetaLicense(int requestCode, String feature) {
		Intent i = new Intent();
		i.setComponent(new ComponentName("to.rcpt.license.fefi", "to.rcpt.license.fefi.LicensingActivity"));
		i.setAction("to.rcpt.license.CHECK");
		i.putExtra("to.rcpt.license.feature", feature);
		i.putExtra("to.rcpt.license.version", 1);
		try {
			startActivityForResult(i, requestCode);
		} catch(ActivityNotFoundException e) {
			AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setMessage("You need to buy a beta license from the market to use this feature.")
			 .setPositiveButton("Tell Me More?", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=to.rcpt.license.fefi")));
				}
			 })
			 .setNegativeButton("Never mind!", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			}).show();
		}
		return false;
	}
}
