package info.wind4869.applogger.Service;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import android.app.ActivityManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import info.wind4869.applogger.BuildConfig;
import info.wind4869.applogger.Tools.Process;
import info.wind4869.applogger.Tools.Utility;

public class LogService extends Service {

	private static final String TAG = LogService.class.getSimpleName();

	public LogService() {
		numOfInstalledApp = 0;
		prevProcess = null;
		curProcess = null;
		isScreenOn = false;

		nameInfoMap = new HashMap<>();
        utility = new Utility();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "LogService onCreate");

		Logging(); // start the logging thread
		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "LogService onStartCommand");

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "LogService onDestroy");

		// restart service
        Intent intent = new Intent(this, LogService.class);
        startService(intent);
    }

	private void Logging() {
		Log.d(TAG, "start logging ... ");

		new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					initOtherMembers(); // init other members
					logAppUsages(); // the main routine
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						Log.e(TAG, "InterruptedException in log thread");
						break;
					}
				}
			}
		}).start();
	}

	private void initOtherMembers() {

		Log.d(TAG, "initOtherMembers");

		// get PowerManager
		powerManager = (PowerManager) getSystemService(POWER_SERVICE);

		// get ActivityManager
		activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

		// get PackageManager
		packageManager = getPackageManager();

		// get ApplicationInfo of all applications installed
		applicationInfos = packageManager.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES);

		// update the <String(processName): ApplicationInfo> map
		if (applicationInfos.size() != numOfInstalledApp) {
			Log.d(TAG, "number of app changed");

			numOfInstalledApp = applicationInfos.size();
			for (ApplicationInfo ai : applicationInfos) {
				nameInfoMap.put(ai.processName, ai);
			}
		}
	}

	private void logAppUsages() {

		// get the switches of screen
		// 这里表示的状态应该是有锁屏到亮屏，或者由由亮屏到锁屏的状态
		if (powerManager.isScreenOn() ^ isScreenOn) {
			isScreenOn = !isScreenOn;

			if (isScreenOn) {
				utility.writeRecordToExternalStorage(
                        (new StringBuilder()).append("[").append(utility.getCurrentTime()).toString());
				Log.d(TAG, "session starts");
			} else {
                utility.writeRecordToExternalStorage(
                        (new StringBuilder()).append("]").append(utility.getCurrentTime()).toString());
				Log.d(TAG, "session ends");
			}
		}

		// get name of current process (deprecated after android 5.0)
//		String processName = activityManager.getRunningTasks(1).get(0).topActivity.getPackageName();

		// deprecated
//		ActivityManager.RunningTaskInfo info = activityManager.getRunningTasks(1).get(0);

		/***
		 * 在 5.0 之后 Google 放弃了 getRunningTasks 函数调用，如果想要获得最近的 App 使用情况，可以参考以下几个网站
		 * https://stackoverflow.com/questions/24625936/getrunningtasks-doesnt-work-in-android-l/28277427#28277427
		 * https://stackoverflow.com/questions/24590533/how-to-get-recent-tasks-on-android-l/26885469#26885469
		 * https://stackoverflow.com/questions/3873659/android-how-can-i-get-the-current-foreground-activity-from-a-service/27642535#27642535
		 */

		String topPackageName = "";
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
			UsageStatsManager manager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
			long time = System.currentTimeMillis();
			//
			List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000*2, time);
			if (stats != null) {
				SortedMap<Long, UsageStats> map = new TreeMap<>();
				for (UsageStats us: stats) {
					map.put(us.getLastTimeUsed(), us);
				}
				if (!map.isEmpty()) {
					UsageStats usageStats = map.get(map.lastKey());
					StringBuilder b = new StringBuilder();
					b.append("---------------------------\n");
					b.append("packageName : ");
					b.append(usageStats.getPackageName());
					b.append("\n");
					b.append("firstTimeStamp : ");
					b.append(usageStats.getFirstTimeStamp());
					b.append("\n");
					b.append("lastTimeStamp : ");
					b.append(usageStats.getLastTimeStamp());
					b.append("\n");
					b.append("getLastTimeUsed : ");
					b.append(usageStats.getLastTimeUsed());
					b.append("\n");
					b.append("getTotalTimeInForeground : ");
					b.append(usageStats.getTotalTimeInForeground());
					b.append("\n---------------------------");

					topPackageName =  map.get(map.lastKey()).getPackageName();
					Log.d(TAG, b.toString());
				} else {
					Log.d(TAG, "sortedMap is empty.");
				}
			} else {
				Log.d(TAG, "stats is null.");
			}
		}

		String processName = topPackageName;

		if (!processName.equals("info.wind4869.applogger") && !processName.equals("android")) {

			if (prevProcess == null) {
				curProcess = createProcess(processName);
				if (curProcess == null) return;

				prevProcess = curProcess; // pay attention!

				Log.d(TAG, curProcess.getAppLabel() + " starts at " + curProcess.getStartTime());

			} else if (!processName.equals(prevProcess.getProcessName())) {
				curProcess = createProcess(processName);
				if (curProcess == null) return;

				// end of previous process
				prevProcess.setEndTime(curProcess.getStartTime());
				Log.d(TAG, prevProcess.getAppLabel() + " ends at " + curProcess.getStartTime());

				// write previous process record to file
                utility.writeRecordToExternalStorage(prevProcess.toString());

				// start of current process
				prevProcess = curProcess; // pay attention!
				Log.d(TAG, curProcess.getAppLabel() + " starts at " + curProcess.getStartTime());
			}
		}
	}

	private Process createProcess(String processName) {
		// get ApplicationInfo by processName from nameInfoMap
		ApplicationInfo applicationInfo = nameInfoMap.get(processName);
		if (applicationInfo == null) return null;

		// get appLabel and uid from ApplicationInfo
		String appLabel = applicationInfo.loadLabel(packageManager).toString();
		String uid = String.valueOf(applicationInfo.uid);

		return new Process(processName, appLabel, utility.getCurrentTime(), null, uid);
	}

	private int numOfInstalledApp;
	private Process prevProcess;
	private Process curProcess;
	private boolean isScreenOn;

	private HashMap<String, ApplicationInfo> nameInfoMap;
    private Utility utility;

	private PowerManager powerManager;
	private ActivityManager activityManager;
	private PackageManager packageManager;
	List<ApplicationInfo> applicationInfos;
}
